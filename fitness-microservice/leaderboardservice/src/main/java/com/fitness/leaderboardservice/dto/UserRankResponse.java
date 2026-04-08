package com.fitness.leaderboardservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserRankResponse {
    private String userId;
    private String metric;
    private String unit;
    private String period;
    private String periodKey;

    private long rank;              // 1-based rank
    private long totalParticipants;
    private Double score;
    private Integer activityCount;

    private Double topScore;        // leader's score (for context)
    private Double percentile;      // 0–100, higher = better
}
