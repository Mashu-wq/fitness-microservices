package com.fitness.goalservice.dto;

import com.fitness.goalservice.model.GoalPeriod;
import com.fitness.goalservice.model.GoalStatus;
import com.fitness.goalservice.model.GoalType;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class GoalResponse {
    private String id;
    private String userId;
    private String title;
    private String description;
    private GoalType type;
    private String targetActivityType;
    private Double targetValue;
    private String unit;
    private GoalPeriod period;
    private LocalDate startDate;
    private LocalDate endDate;
    private GoalStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
