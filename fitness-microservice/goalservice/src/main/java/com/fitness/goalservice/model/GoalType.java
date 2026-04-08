package com.fitness.goalservice.model;

/**
 * The metric a goal measures:
 *   DISTANCE  — total km (requires additionalMetrics.distanceKm on activity)
 *   CALORIES  — total kcal burned (from activity.caloriesBurned)
 *   DURATION  — total minutes active (from activity.duration)
 *   FREQUENCY — number of activity sessions logged
 */
public enum GoalType {
    DISTANCE,
    CALORIES,
    DURATION,
    FREQUENCY
}
