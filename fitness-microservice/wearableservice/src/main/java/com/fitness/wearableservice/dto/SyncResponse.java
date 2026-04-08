package com.fitness.wearableservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SyncResponse {
    private String userId;
    private int eventsProcessed;
    private int activitiesCreated;
    private String message;
    private LocalDateTime syncedAt;
}
