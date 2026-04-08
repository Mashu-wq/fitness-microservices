package com.fitness.wearableservice.service;

import com.fitness.wearableservice.dto.ActivityRequest;
import com.fitness.wearableservice.dto.SyncResponse;
import com.fitness.wearableservice.model.EventType;
import com.fitness.wearableservice.model.WearableEvent;
import com.fitness.wearableservice.repository.WearableEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Aggregates a window of wearable events into a single Activity.
 *
 * Two entry points:
 *   1. aggregateWorkoutSession — triggered by WORKOUT_END Kafka event.
 *      Looks up the matching WORKOUT_START and sums all events in the window.
 *
 *   2. manualSync — triggered by POST /api/wearables/sync.
 *      Rolls up ALL unprocessed events for the user into one activity.
 *      Useful for devices that don't send WORKOUT_START/END signals.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkoutAggregationService {

    private final WearableEventRepository repository;
    private final ActivityPublisher activityPublisher;

    /**
     * Called when a WORKOUT_END event arrives on Kafka.
     * Finds the most recent unprocessed WORKOUT_START for the same user,
     * aggregates all events in [start, end], publishes to ActivityService,
     * and marks all session events as processed.
     *
     * @return true if an activity was created; false if no matching start was found
     */
    public boolean aggregateWorkoutSession(WearableEvent workoutEnd) {
        String userId = workoutEnd.getUserId();

        WearableEvent start = repository
                .findTopByUserIdAndEventTypeAndProcessedFalseOrderByTimestampDesc(userId, EventType.WORKOUT_START)
                .orElse(null);

        if (start == null) {
            log.warn("WORKOUT_END received for user {} but no unprocessed WORKOUT_START found — skipping", userId);
            markProcessed(List.of(workoutEnd));
            return false;
        }

        LocalDateTime from = start.getTimestamp();
        LocalDateTime to   = workoutEnd.getTimestamp();

        List<WearableEvent> sessionEvents = repository
                .findByUserIdAndTimestampBetweenOrderByTimestampAsc(userId, from, to);

        ActivityRequest request = buildActivityRequest(
                sessionEvents,
                resolveActivityType(start, workoutEnd),
                from,
                to);

        activityPublisher.publish(userId, request);

        markProcessed(sessionEvents);
        if (!sessionEvents.contains(workoutEnd)) {
            markProcessed(List.of(workoutEnd));
        }

        log.info("Workout session aggregated: user={} events={} duration={}min",
                userId, sessionEvents.size(), request.getDuration());
        return true;
    }

    /**
     * Manual sync: rolls up ALL unprocessed events for the user into one activity.
     * Used for devices without WORKOUT_START/END or for catch-up scenarios.
     */
    public SyncResponse manualSync(String userId) {
        List<WearableEvent> pending = repository
                .findByUserIdAndProcessedFalseOrderByTimestampAsc(userId);

        if (pending.isEmpty()) {
            return SyncResponse.builder()
                    .userId(userId)
                    .eventsProcessed(0)
                    .activitiesCreated(0)
                    .message("No unprocessed events found.")
                    .syncedAt(LocalDateTime.now())
                    .build();
        }

        LocalDateTime from = pending.get(0).getTimestamp();
        LocalDateTime to   = pending.get(pending.size() - 1).getTimestamp();

        // Determine activity type from WORKOUT_START if present, otherwise OTHER
        String activityType = pending.stream()
                .filter(e -> e.getEventType() == EventType.WORKOUT_START && e.getActivityType() != null)
                .map(WearableEvent::getActivityType)
                .findFirst()
                .orElse("OTHER");

        ActivityRequest request = buildActivityRequest(pending, activityType, from, to);
        activityPublisher.publish(userId, request);
        markProcessed(pending);

        return SyncResponse.builder()
                .userId(userId)
                .eventsProcessed(pending.size())
                .activitiesCreated(1)
                .message("Synced " + pending.size() + " events into 1 activity.")
                .syncedAt(LocalDateTime.now())
                .build();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private ActivityRequest buildActivityRequest(List<WearableEvent> events,
                                                 String activityType,
                                                 LocalDateTime from,
                                                 LocalDateTime to) {
        int durationMinutes = (int) Math.max(1, ChronoUnit.MINUTES.between(from, to));

        int totalCalories = events.stream()
                .filter(e -> e.getCaloriesBurned() != null)
                .mapToInt(WearableEvent::getCaloriesBurned)
                .sum();

        double totalDistanceKm = events.stream()
                .filter(e -> e.getDistanceKm() != null)
                .mapToDouble(WearableEvent::getDistanceKm)
                .sum();

        int totalSteps = events.stream()
                .filter(e -> e.getSteps() != null)
                .mapToInt(WearableEvent::getSteps)
                .sum();

        OptionalDouble avgHeartRate = events.stream()
                .filter(e -> e.getHeartRate() != null)
                .mapToInt(WearableEvent::getHeartRate)
                .average();

        int peakHeartRate = events.stream()
                .filter(e -> e.getHeartRate() != null)
                .mapToInt(WearableEvent::getHeartRate)
                .max()
                .orElse(0);

        Map<String, Object> additionalMetrics = new HashMap<>();
        if (totalDistanceKm > 0) additionalMetrics.put("distanceKm", totalDistanceKm);
        if (totalSteps > 0)      additionalMetrics.put("steps", totalSteps);
        if (avgHeartRate.isPresent()) {
            additionalMetrics.put("avgHeartRate", (int) avgHeartRate.getAsDouble());
            additionalMetrics.put("peakHeartRate", peakHeartRate);
        }
        additionalMetrics.put("source", "wearable");
        additionalMetrics.put("eventCount", events.size());

        return ActivityRequest.builder()
                .type(activityType)
                .duration(durationMinutes)
                .caloriesBurned(Math.max(0, totalCalories))
                .startTime(from)
                .additionalMetrics(additionalMetrics)
                .build();
    }

    private String resolveActivityType(WearableEvent start, WearableEvent end) {
        if (end.getActivityType() != null) return end.getActivityType();
        if (start.getActivityType() != null) return start.getActivityType();
        return "OTHER";
    }

    private void markProcessed(List<WearableEvent> events) {
        events.forEach(e -> e.setProcessed(true));
        repository.saveAll(events);
    }
}
