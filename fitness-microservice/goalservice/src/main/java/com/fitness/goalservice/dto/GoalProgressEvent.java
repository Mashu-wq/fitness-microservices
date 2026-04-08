package com.fitness.goalservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Published to goal.progress.exchange when a user crosses a progress milestone (25/50/75/100%).
 * Consumed by AIService to generate adaptive coaching recommendations.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GoalProgressEvent implements Serializable {
    private String goalId;
    private String userId;
    private String goalTitle;
    private String goalType;            // GoalType.name()
    private String targetActivityType;  // nullable
    private Double targetValue;
    private Double currentValue;
    private Double percentageComplete;
    private String unit;
    private Double milestone;           // 25.0, 50.0, 75.0, or 100.0
    private String startDate;           // ISO date string
    private String endDate;
    private long daysRemaining;
}
