package com.fitness.goalservice.model;

public enum GoalStatus {
    ACTIVE,
    COMPLETED,   // currentValue >= targetValue
    FAILED,      // endDate passed with currentValue < targetValue
    PAUSED       // user-paused, no progress tracking while paused
}
