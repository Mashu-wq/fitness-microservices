package com.fitness.goalservice.dto;

import com.fitness.goalservice.model.GoalStatus;
import com.fitness.goalservice.model.GoalType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class GoalProgressResponse {
    private String goalId;
    private String userId;
    private String goalTitle;
    private GoalType goalType;
    private String unit;
    private Double targetValue;
    private Double currentValue;
    private Double percentageComplete;
    private Boolean completed;
    private GoalStatus goalStatus;
    private LocalDate startDate;
    private LocalDate endDate;
    private long daysRemaining;
    private LocalDateTime lastUpdated;
}
