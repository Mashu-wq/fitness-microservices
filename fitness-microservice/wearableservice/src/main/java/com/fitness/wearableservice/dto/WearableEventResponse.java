package com.fitness.wearableservice.dto;

import com.fitness.wearableservice.model.DeviceType;
import com.fitness.wearableservice.model.EventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WearableEventResponse {
    private String id;
    private String userId;
    private DeviceType deviceType;
    private EventType  eventType;
    private String activityType;
    private LocalDateTime timestamp;
    private Integer heartRate;
    private Integer steps;
    private Integer caloriesBurned;
    private Double  distanceKm;
    private Boolean processed;
    private LocalDateTime receivedAt;
}
