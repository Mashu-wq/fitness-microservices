package com.fitness.leaderboardservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * One score document per (userId × metric × periodKey).
 * Updated atomically via MongoTemplate upsert — no read-modify-write race.
 *
 * Example periodKey values: "2026-W14", "2026-04", "ALL"
 */
@Document(collection = "leaderboard_scores")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@CompoundIndexes({
    // Uniqueness enforced at DB level
    @CompoundIndex(name = "unique_score", def = "{'userId':1,'metric':1,'periodKey':1}", unique = true),
    // Efficient top-N query for a given metric + period, sorted by score DESC
    @CompoundIndex(name = "rank_query",   def = "{'metric':1,'periodKey':1,'score':-1}")
})
public class LeaderboardEntry {

    @Id
    private String id;

    @Indexed
    private String userId;

    private LeaderboardMetric metric;
    private LeaderboardPeriod period;

    /**
     * Bucketing key derived from period:
     *   WEEKLY   → "2026-W14"
     *   MONTHLY  → "2026-04"
     *   ALL_TIME → "ALL"
     */
    private String periodKey;

    private Double score;          // cumulative metric total for this period
    private Integer activityCount; // number of activities that contributed

    private LocalDateTime updatedAt;

    @CreatedDate
    private LocalDateTime createdAt;
}
