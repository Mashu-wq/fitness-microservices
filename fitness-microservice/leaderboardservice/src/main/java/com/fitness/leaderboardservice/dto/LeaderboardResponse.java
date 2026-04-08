package com.fitness.leaderboardservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LeaderboardResponse {

    private String metric;
    private String unit;
    private String period;
    private String periodKey;
    private int totalParticipants;
    private LocalDateTime generatedAt;
    private List<RankedEntry> entries;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RankedEntry {
        private int rank;
        private String userId;
        private Double score;
        private Integer activityCount;
    }
}
