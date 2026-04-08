package com.fitness.leaderboardservice.service;

import com.fitness.leaderboardservice.model.Activity;
import com.fitness.leaderboardservice.model.LeaderboardEntry;
import com.fitness.leaderboardservice.model.LeaderboardMetric;
import com.fitness.leaderboardservice.model.LeaderboardPeriod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Processes an incoming activity and atomically increments scores for
 * every metric × period combination using MongoDB upsert.
 *
 * An upsert is used (not read-modify-write) so concurrent messages for
 * the same user can never corrupt the score.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScoreUpdateService {

    private final MongoTemplate mongoTemplate;

    /**
     * Extracts all metric contributions from the activity and upserts scores
     * for every (metric × period) bucket.
     * Evicts the leaderboard cache so stale data isn't served.
     */
    @CacheEvict(value = "leaderboard", allEntries = true)
    public void processActivity(Activity activity) {
        LocalDate activityDate = activity.getStartTime() != null
                ? activity.getStartTime().toLocalDate()
                : LocalDate.now();

        for (LeaderboardMetric metric : LeaderboardMetric.values()) {
            double contribution = extractContribution(metric, activity);
            if (contribution <= 0) continue;

            for (LeaderboardPeriod period : LeaderboardPeriod.values()) {
                String periodKey = period.toPeriodKey(activityDate);
                upsertScore(activity.getUserId(), metric, period, periodKey, contribution);
            }
        }

        log.info("Leaderboard scores updated for user={} activity={}", activity.getUserId(), activity.getId());
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private double extractContribution(LeaderboardMetric metric, Activity activity) {
        return switch (metric) {
            case DISTANCE -> {
                if (activity.getAdditionalMetrics() == null) yield 0.0;
                Object raw = activity.getAdditionalMetrics().get("distanceKm");
                if (raw == null) yield 0.0;
                try {
                    yield Double.parseDouble(raw.toString());
                } catch (NumberFormatException e) {
                    log.warn("distanceKm is not a number: {}", raw);
                    yield 0.0;
                }
            }
            case CALORIES -> activity.getCaloriesBurned() != null
                    ? activity.getCaloriesBurned().doubleValue() : 0.0;
            case DURATION -> activity.getDuration() != null
                    ? activity.getDuration().doubleValue() : 0.0;
            case FREQUENCY -> 1.0;
        };
    }

    /**
     * Atomic increment via findAndModify with upsert=true.
     * On insert, setOnInsert initialises the document fields.
     * On update, $inc accumulates the score and activityCount.
     */
    private void upsertScore(String userId, LeaderboardMetric metric,
                             LeaderboardPeriod period, String periodKey, double contribution) {
        Query query = Query.query(
                Criteria.where("userId").is(userId)
                        .and("metric").is(metric)
                        .and("periodKey").is(periodKey));

        Update update = new Update()
                .inc("score", contribution)
                .inc("activityCount", 1)
                .set("updatedAt", LocalDateTime.now())
                .setOnInsert("userId", userId)
                .setOnInsert("metric", metric)
                .setOnInsert("period", period)
                .setOnInsert("periodKey", periodKey);

        mongoTemplate.findAndModify(
                query, update,
                FindAndModifyOptions.options().upsert(true).returnNew(false),
                LeaderboardEntry.class);
    }
}
