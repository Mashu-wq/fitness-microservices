package com.fitness.goalservice.dto;

import com.fitness.goalservice.model.GoalPeriod;
import com.fitness.goalservice.model.GoalType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class GoalRequest {

    @NotBlank(message = "Goal title is required")
    private String title;

    private String description;

    @NotNull(message = "Goal type is required (DISTANCE, CALORIES, DURATION, FREQUENCY)")
    private GoalType type;

    // null = any activity counts toward this goal
    private String targetActivityType;

    @NotNull(message = "Target value is required")
    @DecimalMin(value = "0.01", message = "Target value must be greater than zero")
    private Double targetValue;

    @NotBlank(message = "Unit is required (KM, KCAL, MINUTES, SESSIONS)")
    private String unit;

    @NotNull(message = "Period is required (WEEKLY, MONTHLY, CUSTOM)")
    private GoalPeriod period;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;
}
