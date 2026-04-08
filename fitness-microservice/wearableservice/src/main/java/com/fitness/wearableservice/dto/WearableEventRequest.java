package com.fitness.wearableservice.dto;

import com.fitness.wearableservice.model.DeviceType;
import com.fitness.wearableservice.model.EventType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class WearableEventRequest {

    @NotNull(message = "deviceType is required")
    private DeviceType deviceType;

    @NotNull(message = "eventType is required")
    private EventType eventType;

    /**
     * Activity type string for WORKOUT_START / WORKOUT_END events.
     * Must match one of ActivityService's ActivityType values:
     * RUNNING, WALKING, CYCLING, SWIMMING, WEIGHT_TRAINING, YOGA, HIIT, CARDIO, STRETCHING, OTHER
     */
    private String activityType;  // nullable for non-workout events

    /** Device-reported event time. Defaults to server time if null. */
    private LocalDateTime timestamp;

    private Integer heartRate;
    private Integer steps;
    private Integer caloriesBurned;
    private Double  distanceKm;
    private Double  latitude;
    private Double  longitude;
    private Double  altitude;

    private Map<String, Object> rawData;
}
