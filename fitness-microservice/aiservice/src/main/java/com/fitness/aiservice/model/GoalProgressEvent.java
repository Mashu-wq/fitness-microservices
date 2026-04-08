package com.fitness.aiservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Deserialization model for goal milestone events consumed from goal.ai.queue.
 * Field names must exactly match what goalservice publishes as JSON.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GoalProgressEvent {
    private String goalId;
    private String userId;
    private String goalTitle;
    private String goalType;
    private String targetActivityType;
    private Double targetValue;
    private Double currentValue;
    private Double percentageComplete;
    private String unit;
    private Double milestone;          // 25.0 | 50.0 | 75.0 | 100.0
    private String startDate;
    private String endDate;
    private long daysRemaining;
}
