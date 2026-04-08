package com.fitness.goalservice.model;

public enum GoalPeriod {
    WEEKLY,   // 7-day window from startDate
    MONTHLY,  // calendar month
    CUSTOM    // user-defined startDate → endDate
}
