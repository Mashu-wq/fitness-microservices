package com.fitness.goalservice.service;

import com.fitness.goalservice.dto.GoalProgressResponse;
import com.fitness.goalservice.dto.GoalRequest;
import com.fitness.goalservice.dto.GoalResponse;
import com.fitness.goalservice.model.Goal;
import com.fitness.goalservice.model.GoalPeriod;
import com.fitness.goalservice.model.GoalProgress;
import com.fitness.goalservice.model.GoalStatus;
import com.fitness.goalservice.repository.GoalProgressRepository;
import com.fitness.goalservice.repository.GoalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoalService {

    private final GoalRepository goalRepository;
    private final GoalProgressRepository goalProgressRepository;

    public GoalResponse createGoal(String userId, GoalRequest request) {
        LocalDate start = resolveStartDate(request);
        LocalDate end = resolveEndDate(request, start);

        Goal goal = Goal.builder()
                .userId(userId)
                .title(request.getTitle())
                .description(request.getDescription())
                .type(request.getType())
                .targetActivityType(request.getTargetActivityType())
                .targetValue(request.getTargetValue())
                .unit(request.getUnit())
                .period(request.getPeriod())
                .startDate(start)
                .endDate(end)
                .status(GoalStatus.ACTIVE)
                .build();

        Goal saved = goalRepository.save(goal);

        // Seed an empty progress document so queries always return a value
        GoalProgress progress = GoalProgress.builder()
                .goalId(saved.getId())
                .userId(userId)
                .targetValue(request.getTargetValue())
                .currentValue(0.0)
                .percentageComplete(0.0)
                .completed(false)
                .build();
        goalProgressRepository.save(progress);

        log.info("Created goal '{}' for user {}", saved.getTitle(), userId);
        return mapToGoalResponse(saved);
    }

    public List<GoalResponse> getUserGoals(String userId) {
        return goalRepository.findByUserId(userId)
                .stream()
                .map(this::mapToGoalResponse)
                .toList();
    }

    public GoalResponse getGoalById(String goalId, String userId) {
        Goal goal = getGoalAndVerifyOwnership(goalId, userId);
        return mapToGoalResponse(goal);
    }

    public GoalProgressResponse getGoalProgress(String goalId, String userId) {
        Goal goal = getGoalAndVerifyOwnership(goalId, userId);
        GoalProgress progress = goalProgressRepository.findByGoalId(goalId)
                .orElseThrow(() -> new RuntimeException("Progress not found for goal: " + goalId));
        return mapToProgressResponse(goal, progress);
    }

    public List<GoalProgressResponse> getAllProgressForUser(String userId) {
        List<GoalProgress> progressList = goalProgressRepository.findByUserId(userId);
        return progressList.stream()
                .map(p -> {
                    Goal goal = goalRepository.findById(p.getGoalId())
                            .orElse(null);
                    return goal != null ? mapToProgressResponse(goal, p) : null;
                })
                .filter(p -> p != null)
                .toList();
    }

    public void pauseGoal(String goalId, String userId) {
        Goal goal = getGoalAndVerifyOwnership(goalId, userId);
        goal.setStatus(GoalStatus.PAUSED);
        goalRepository.save(goal);
        log.info("Paused goal {} for user {}", goalId, userId);
    }

    public void resumeGoal(String goalId, String userId) {
        Goal goal = getGoalAndVerifyOwnership(goalId, userId);
        if (goal.getStatus() != GoalStatus.PAUSED) {
            throw new RuntimeException("Goal is not paused — current status: " + goal.getStatus());
        }
        goal.setStatus(GoalStatus.ACTIVE);
        goalRepository.save(goal);
        log.info("Resumed goal {} for user {}", goalId, userId);
    }

    public void deleteGoal(String goalId, String userId) {
        Goal goal = getGoalAndVerifyOwnership(goalId, userId);
        goalProgressRepository.findByGoalId(goalId)
                .ifPresent(goalProgressRepository::delete);
        goalRepository.delete(goal);
        log.info("Deleted goal {} for user {}", goalId, userId);
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    private Goal getGoalAndVerifyOwnership(String goalId, String userId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new RuntimeException("Goal not found: " + goalId));
        if (!goal.getUserId().equals(userId)) {
            throw new SecurityException("Access denied to goal: " + goalId);
        }
        return goal;
    }

    private LocalDate resolveStartDate(GoalRequest request) {
        return request.getStartDate() != null ? request.getStartDate() : LocalDate.now();
    }

    private LocalDate resolveEndDate(GoalRequest request, LocalDate start) {
        if (request.getEndDate() != null) return request.getEndDate();
        return switch (request.getPeriod()) {
            case WEEKLY -> start.plusWeeks(1);
            case MONTHLY -> start.plusMonths(1);
            case CUSTOM -> start.plusDays(30); // safe fallback
        };
    }

    public GoalResponse mapToGoalResponse(Goal goal) {
        GoalResponse r = new GoalResponse();
        r.setId(goal.getId());
        r.setUserId(goal.getUserId());
        r.setTitle(goal.getTitle());
        r.setDescription(goal.getDescription());
        r.setType(goal.getType());
        r.setTargetActivityType(goal.getTargetActivityType());
        r.setTargetValue(goal.getTargetValue());
        r.setUnit(goal.getUnit());
        r.setPeriod(goal.getPeriod());
        r.setStartDate(goal.getStartDate());
        r.setEndDate(goal.getEndDate());
        r.setStatus(goal.getStatus());
        r.setCreatedAt(goal.getCreatedAt());
        r.setUpdatedAt(goal.getUpdatedAt());
        return r;
    }

    public GoalProgressResponse mapToProgressResponse(Goal goal, GoalProgress progress) {
        GoalProgressResponse r = new GoalProgressResponse();
        r.setGoalId(goal.getId());
        r.setUserId(goal.getUserId());
        r.setGoalTitle(goal.getTitle());
        r.setGoalType(goal.getType());
        r.setUnit(goal.getUnit());
        r.setTargetValue(progress.getTargetValue());
        r.setCurrentValue(progress.getCurrentValue());
        r.setPercentageComplete(progress.getPercentageComplete());
        r.setCompleted(progress.getCompleted());
        r.setGoalStatus(goal.getStatus());
        r.setStartDate(goal.getStartDate());
        r.setEndDate(goal.getEndDate());
        r.setDaysRemaining(Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), goal.getEndDate())));
        r.setLastUpdated(progress.getLastUpdated());
        return r;
    }
}
