package com.fitness.activityservice.dto;

import com.fitness.activityservice.model.ActivityType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class ActivityResponse {
    private String id;
    private String userId;
    private ActivityType type;
    private Integer duration;
    private Integer caloriesBurned;
    private LocalDateTime startTime;
    // FIX: Renamed from additionalMetrices (typo). Removed MongoDB-specific annotations — they don't belong in a DTO
    private Map<String, Object> additionalMetrics;
    private LocalDateTime createdAt;
    // FIX: Renamed from updateadAt (typo)
    private LocalDateTime updatedAt;
}
