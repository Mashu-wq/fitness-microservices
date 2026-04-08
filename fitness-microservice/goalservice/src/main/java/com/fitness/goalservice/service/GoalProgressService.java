package com.fitness.goalservice.service;

import com.fitness.goalservice.dto.GoalProgressEvent;
import com.fitness.goalservice.model.Activity;
import com.fitness.goalservice.model.Goal;
import com.fitness.goalservice.model.GoalProgress;
import com.fitness.goalservice.model.GoalStatus;
import com.fitness.goalservice.model.GoalType;
import com.fitness.goalservice.repository.GoalProgressRepository;
import com.fitness.goalservice.repository.GoalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoalProgressService {

    private static final List<Double> MILESTONES = List.of(25.0, 50.0, 75.0, 100.0);
    private static final String GOAL_PROGRESS_EXCHANGE = "goal.progress.exchange";
    private static final String GOAL_PROGRESS_ROUTING_KEY = "goal.progress";

    private final GoalRepository goalRepository;
    private final GoalProgressRepository goalProgressRepository;
    private final RabbitTemplate rabbitTemplate;
    private final SseEmitterService sseEmitterService;

    /**
     * Entry point called by ActivityEventListener for each arriving activity.
     * Finds all ACTIVE goals for the user that overlap with the activity date,
     * then updates progress for each matching goal.
     */
    public void processActivity(Activity activity) {
        String userId = activity.getUserId();
        LocalDate activityDate = activity.getStartTime() != null
                ? activity.getStartTime().toLocalDate()
                : LocalDate.now();

        List<Goal> activeGoals = goalRepository.findByUserIdAndStatus(userId, GoalStatus.ACTIVE);
        if (activeGoals.isEmpty()) {
            log.debug("No active goals for user {}", userId);
            return;
        }

        for (Goal goal : activeGoals) {
            if (!isDateInRange(activityDate, goal.getStartDate(), goal.getEndDate())) {
                continue;
            }
            if (!isActivityRelevant(goal, activity)) {
                continue;
            }

            double contribution = extractContribution(goal.getType(), activity);
            if (contribution <= 0) continue;

            updateProgress(goal, contribution, activity);
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private boolean isDateInRange(LocalDate date, LocalDate start, LocalDate end) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    /**
     * A goal is relevant when targetActivityType is null (any type) OR matches the
     * activity's type string (case-insensitive).
     */
    private boolean isActivityRelevant(Goal goal, Activity activity) {
        if (goal.getTargetActivityType() == null || goal.getTargetActivityType().isBlank()) {
            return true;
        }
        return goal.getTargetActivityType().equalsIgnoreCase(activity.getType());
    }

    /**
     * Maps GoalType → the numeric contribution from this single activity.
     *
     * DISTANCE  — reads additionalMetrics["distanceKm"]  (client must supply this)
     * CALORIES  — caloriesBurned field
     * DURATION  — duration (minutes)
     * FREQUENCY — always 1.0 (each qualifying activity = 1 session)
     */
    private double extractContribution(GoalType type, Activity activity) {
        return switch (type) {
            case DISTANCE -> {
                if (activity.getAdditionalMetrics() == null) yield 0.0;
                Object raw = activity.getAdditionalMetrics().get("distanceKm");
                if (raw == null) yield 0.0;
                try {
                    yield Double.parseDouble(raw.toString());
                } catch (NumberFormatException e) {
                    log.warn("distanceKm is not a number: {}", raw);
                    yield 0.0;
                }
            }
            case CALORIES -> activity.getCaloriesBurned() != null
                    ? activity.getCaloriesBurned().doubleValue()
                    : 0.0;
            case DURATION -> activity.getDuration() != null
                    ? activity.getDuration().doubleValue()
                    : 0.0;
            case FREQUENCY -> 1.0;
        };
    }

    private void updateProgress(Goal goal, double contribution, Activity activity) {
        GoalProgress progress = goalProgressRepository.findByGoalId(goal.getId())
                .orElseGet(() -> GoalProgress.builder()
                        .goalId(goal.getId())
                        .userId(goal.getUserId())
                        .targetValue(goal.getTargetValue())
                        .currentValue(0.0)
                        .percentageComplete(0.0)
                        .completed(false)
                        .build());

        double previousValue = progress.getCurrentValue();
        double newValue = previousValue + contribution;
        double pct = Math.min(100.0, (newValue / goal.getTargetValue()) * 100.0);

        progress.setCurrentValue(newValue);
        progress.setPercentageComplete(pct);
        progress.setLastUpdated(LocalDateTime.now());

        if (pct >= 100.0 && !progress.getCompleted()) {
            progress.setCompleted(true);
            goal.setStatus(GoalStatus.COMPLETED);
            goalRepository.save(goal);
            log.info("Goal {} COMPLETED for user {}", goal.getId(), goal.getUserId());
        }

        goalProgressRepository.save(progress);
        log.debug("Goal {} progress: {}/{} ({}%)", goal.getId(), newValue, goal.getTargetValue(), String.format("%.1f", pct));

        checkAndPublishMilestones(goal, progress, previousValue, newValue);

        // Push real-time SSE update
        sseEmitterService.pushProgressUpdate(goal.getUserId(), buildProgressEvent(goal, progress));
    }

    /**
     * Detects when a new milestone (25/50/75/100) is crossed for the first time
     * and publishes a GoalProgressEvent to RabbitMQ for the AIService to process.
     */
    private void checkAndPublishMilestones(Goal goal, GoalProgress progress,
                                           double previousValue, double newValue) {
        double target = goal.getTargetValue();
        double previousPct = (previousValue / target) * 100.0;
        double currentPct = (newValue / target) * 100.0;

        for (Double milestone : MILESTONES) {
            if (previousPct < milestone && currentPct >= milestone
                    && !progress.getNotifiedMilestones().contains(milestone)) {

                progress.getNotifiedMilestones().add(milestone);
                goalProgressRepository.save(progress); // persist milestone record

                GoalProgressEvent event = buildProgressEvent(goal, progress);
                event.setMilestone(milestone);

                rabbitTemplate.convertAndSend(GOAL_PROGRESS_EXCHANGE, GOAL_PROGRESS_ROUTING_KEY, event);
                log.info("Published {}% milestone event for goal {} (user {})",
                        milestone.intValue(), goal.getId(), goal.getUserId());
            }
        }
    }

    private GoalProgressEvent buildProgressEvent(Goal goal, GoalProgress progress) {
        return GoalProgressEvent.builder()
                .goalId(goal.getId())
                .userId(goal.getUserId())
                .goalTitle(goal.getTitle())
                .goalType(goal.getType().name())
                .targetActivityType(goal.getTargetActivityType())
                .targetValue(progress.getTargetValue())
                .currentValue(progress.getCurrentValue())
                .percentageComplete(progress.getPercentageComplete())
                .unit(goal.getUnit())
                .startDate(goal.getStartDate().toString())
                .endDate(goal.getEndDate().toString())
                .daysRemaining(Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), goal.getEndDate())))
                .build();
    }
}
