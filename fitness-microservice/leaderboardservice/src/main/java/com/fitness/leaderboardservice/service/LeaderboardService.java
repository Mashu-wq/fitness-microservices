package com.fitness.leaderboardservice.service;

import com.fitness.leaderboardservice.dto.LeaderboardResponse;
import com.fitness.leaderboardservice.dto.UserRankResponse;
import com.fitness.leaderboardservice.model.LeaderboardEntry;
import com.fitness.leaderboardservice.model.LeaderboardMetric;
import com.fitness.leaderboardservice.model.LeaderboardPeriod;
import com.fitness.leaderboardservice.repository.LeaderboardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaderboardService {

    private final LeaderboardRepository repository;

    /**
     * Returns the top-N ranked entries for the given metric and period.
     * Results are cached in Redis for 60 seconds (configured in CacheConfig).
     *
     * @param metric one of DISTANCE | CALORIES | DURATION | FREQUENCY
     * @param period one of WEEKLY | MONTHLY | ALL_TIME
     * @param limit  max entries to return (capped at 100 by the controller)
     */
    @Cacheable(value = "leaderboard", key = "#metric.name() + ':' + #period.name() + ':' + #limit")
    public LeaderboardResponse getLeaderboard(LeaderboardMetric metric,
                                              LeaderboardPeriod period,
                                              int limit) {
        String periodKey = period.currentPeriodKey();

        List<LeaderboardEntry> all = repository
                .findByMetricAndPeriodKeyOrderByScoreDesc(metric, periodKey);

        List<LeaderboardEntry> paged = all.stream().limit(limit).toList();

        List<LeaderboardResponse.RankedEntry> ranked = IntStream
                .range(0, paged.size())
                .mapToObj(i -> LeaderboardResponse.RankedEntry.builder()
                        .rank(i + 1)
                        .userId(paged.get(i).getUserId())
                        .score(paged.get(i).getScore())
                        .activityCount(paged.get(i).getActivityCount())
                        .build())
                .toList();

        return LeaderboardResponse.builder()
                .metric(metric.name())
                .unit(metric.getUnit())
                .period(period.name())
                .periodKey(periodKey)
                .totalParticipants(all.size())
                .generatedAt(LocalDateTime.now())
                .entries(ranked)
                .build();
    }

    /**
     * Returns the requesting user's rank for a specific metric and period.
     * Includes rank, total participants, score, top score, and percentile.
     */
    public UserRankResponse getUserRank(String userId,
                                       LeaderboardMetric metric,
                                       LeaderboardPeriod period) {
        String periodKey = period.currentPeriodKey();

        LeaderboardEntry userEntry = repository
                .findByUserIdAndMetricAndPeriodKey(userId, metric, periodKey)
                .orElse(null);

        long totalParticipants = repository.countByMetricAndPeriodKey(metric, periodKey);

        if (userEntry == null) {
            // User has no activity in this period — they're last place
            return UserRankResponse.builder()
                    .userId(userId)
                    .metric(metric.name())
                    .unit(metric.getUnit())
                    .period(period.name())
                    .periodKey(periodKey)
                    .rank(totalParticipants + 1)
                    .totalParticipants(totalParticipants)
                    .score(0.0)
                    .activityCount(0)
                    .topScore(getTopScore(metric, periodKey))
                    .percentile(0.0)
                    .build();
        }

        long countAbove = repository.countByMetricAndPeriodKeyAndScoreGreaterThan(
                metric, periodKey, userEntry.getScore());
        long rank = countAbove + 1;

        // Percentile: % of participants the user is ahead of
        double percentile = totalParticipants <= 1 ? 100.0
                : ((double) (totalParticipants - rank) / (totalParticipants - 1)) * 100.0;

        return UserRankResponse.builder()
                .userId(userId)
                .metric(metric.name())
                .unit(metric.getUnit())
                .period(period.name())
                .periodKey(periodKey)
                .rank(rank)
                .totalParticipants(totalParticipants)
                .score(userEntry.getScore())
                .activityCount(userEntry.getActivityCount())
                .topScore(getTopScore(metric, periodKey))
                .percentile(Math.round(percentile * 10.0) / 10.0)
                .build();
    }

    private Double getTopScore(LeaderboardMetric metric, String periodKey) {
        List<LeaderboardEntry> top = repository
                .findByMetricAndPeriodKeyOrderByScoreDesc(metric, periodKey);
        return top.isEmpty() ? 0.0 : top.get(0).getScore();
    }
}
