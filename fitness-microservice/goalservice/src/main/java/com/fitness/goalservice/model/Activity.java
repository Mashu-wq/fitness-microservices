package com.fitness.goalservice.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Mirror of activityservice's Activity model — used only for RabbitMQ deserialization.
 * Field names must exactly match what activityservice publishes as JSON.
 *
 * DISTANCE goals: read additionalMetrics.distanceKm (set by the client when logging an activity)
 */
@Data
public class Activity {
    private String id;
    private String userId;
    private String type;                        // ActivityType enum as String
    private Integer duration;                   // minutes
    private Integer caloriesBurned;
    private LocalDateTime startTime;
    private Map<String, Object> additionalMetrics;  // may contain "distanceKm"
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
