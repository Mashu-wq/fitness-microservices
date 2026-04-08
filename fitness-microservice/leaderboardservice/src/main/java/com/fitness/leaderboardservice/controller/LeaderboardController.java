package com.fitness.leaderboardservice.controller;

import com.fitness.leaderboardservice.dto.LeaderboardResponse;
import com.fitness.leaderboardservice.dto.UserRankResponse;
import com.fitness.leaderboardservice.model.LeaderboardMetric;
import com.fitness.leaderboardservice.model.LeaderboardPeriod;
import com.fitness.leaderboardservice.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    /**
     * GET /api/leaderboard?metric=DISTANCE&period=WEEKLY&limit=10
     *
     * Returns a ranked list of top users for the given metric and period.
     * Defaults: metric=FREQUENCY, period=WEEKLY, limit=10.
     * Results are cached in Redis for 60 seconds.
     */
    @GetMapping
    public ResponseEntity<LeaderboardResponse> getLeaderboard(
            @RequestParam(defaultValue = "FREQUENCY") LeaderboardMetric metric,
            @RequestParam(defaultValue = "WEEKLY")    LeaderboardPeriod period,
            @RequestParam(defaultValue = "10")        int limit) {

        limit = Math.min(limit, 100); // cap at 100
        return ResponseEntity.ok(leaderboardService.getLeaderboard(metric, period, limit));
    }

    /**
     * GET /api/leaderboard/me?metric=DISTANCE&period=WEEKLY
     *
     * Returns the calling user's rank, score, percentile, and how far they are
     * from the leader. Uses X-User-ID injected by the gateway.
     */
    @GetMapping("/me")
    public ResponseEntity<UserRankResponse> getMyRank(
            @RequestHeader("X-User-ID")              String userId,
            @RequestParam(defaultValue = "FREQUENCY") LeaderboardMetric metric,
            @RequestParam(defaultValue = "WEEKLY")    LeaderboardPeriod period) {

        return ResponseEntity.ok(leaderboardService.getUserRank(userId, metric, period));
    }

    /**
     * GET /api/leaderboard/metrics
     * Returns the list of available metrics with their units.
     */
    @GetMapping("/metrics")
    public ResponseEntity<List<MetricInfo>> getMetrics() {
        List<MetricInfo> metrics = Arrays.stream(LeaderboardMetric.values())
                .map(m -> new MetricInfo(m.name(), m.getUnit()))
                .toList();
        return ResponseEntity.ok(metrics);
    }

    /**
     * GET /api/leaderboard/periods
     * Returns the list of available leaderboard periods.
     */
    @GetMapping("/periods")
    public ResponseEntity<List<String>> getPeriods() {
        List<String> periods = Arrays.stream(LeaderboardPeriod.values())
                .map(Enum::name)
                .toList();
        return ResponseEntity.ok(periods);
    }

    record MetricInfo(String metric, String unit) {}
}
