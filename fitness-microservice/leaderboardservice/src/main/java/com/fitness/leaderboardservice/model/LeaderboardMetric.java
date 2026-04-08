package com.fitness.leaderboardservice.model;

/**
 * The four score dimensions tracked on the leaderboard.
 * Each maps to a field on the incoming Activity event.
 */
public enum LeaderboardMetric {

    DISTANCE("KM"),       // additionalMetrics["distanceKm"]
    CALORIES("KCAL"),     // caloriesBurned
    DURATION("MIN"),      // duration (minutes)
    FREQUENCY("SESSIONS"); // 1 per qualifying activity

    private final String unit;

    LeaderboardMetric(String unit) {
        this.unit = unit;
    }

    public String getUnit() {
        return unit;
    }
}
