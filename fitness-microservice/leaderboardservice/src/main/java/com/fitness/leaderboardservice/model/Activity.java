package com.fitness.leaderboardservice.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Mirror of activityservice's Activity — used only for RabbitMQ deserialization.
 * Field names must exactly match what activityservice publishes as JSON.
 */
@Data
public class Activity {
    private String id;
    private String userId;
    private String type;
    private Integer duration;
    private Integer caloriesBurned;
    private LocalDateTime startTime;
    private Map<String, Object> additionalMetrics;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
