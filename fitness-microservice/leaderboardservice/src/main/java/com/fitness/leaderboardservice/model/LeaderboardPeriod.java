package com.fitness.leaderboardservice.model;

import java.time.LocalDate;
import java.time.temporal.IsoFields;

/**
 * Temporal bucketing for leaderboard scores.
 * Each period maps to a human-readable key stored in MongoDB.
 */
public enum LeaderboardPeriod {

    WEEKLY,   // e.g. "2026-W14"
    MONTHLY,  // e.g. "2026-04"
    ALL_TIME; // "ALL"

    /** Computes the period key for the given date. */
    public String toPeriodKey(LocalDate date) {
        return switch (this) {
            case WEEKLY -> date.getYear() + "-W"
                    + String.format("%02d", date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case MONTHLY -> date.getYear() + "-" + String.format("%02d", date.getMonthValue());
            case ALL_TIME -> "ALL";
        };
    }

    /** Convenience: period key for today. */
    public String currentPeriodKey() {
        return toPeriodKey(LocalDate.now());
    }
}
