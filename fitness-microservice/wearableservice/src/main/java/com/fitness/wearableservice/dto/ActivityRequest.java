package com.fitness.wearableservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Mirrors ActivityService's ActivityRequest DTO.
 * Used when wearableservice calls POST /api/activities to create
 * an activity from an aggregated wearable session.
 * Field names and types must match the ActivityService contract exactly.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActivityRequest {
    private String type;             // ActivityType enum name: RUNNING, CYCLING, etc.
    private Integer duration;        // minutes
    private Integer caloriesBurned;
    private LocalDateTime startTime;
    private Map<String, Object> additionalMetrics;
}
