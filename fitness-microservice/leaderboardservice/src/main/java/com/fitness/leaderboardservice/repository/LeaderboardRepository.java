package com.fitness.leaderboardservice.repository;

import com.fitness.leaderboardservice.model.LeaderboardEntry;
import com.fitness.leaderboardservice.model.LeaderboardMetric;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaderboardRepository extends MongoRepository<LeaderboardEntry, String> {

    /**
     * Top-N query: sorted DESC by score at DB level using the rank_query compound index.
     * limit() is applied by Spring Data — pass a PageRequest or use findTopN variants.
     */
    List<LeaderboardEntry> findByMetricAndPeriodKeyOrderByScoreDesc(
            LeaderboardMetric metric, String periodKey);

    Optional<LeaderboardEntry> findByUserIdAndMetricAndPeriodKey(
            String userId, LeaderboardMetric metric, String periodKey);

    /**
     * Counts how many users have a HIGHER score — gives (rank - 1).
     * Rank = countAbove + 1.
     */
    long countByMetricAndPeriodKeyAndScoreGreaterThan(
            LeaderboardMetric metric, String periodKey, Double score);

    /** Total participants for a metric+period — denominator for percentile. */
    long countByMetricAndPeriodKey(LeaderboardMetric metric, String periodKey);
}
