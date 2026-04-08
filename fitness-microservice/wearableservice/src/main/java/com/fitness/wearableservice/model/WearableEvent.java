package com.fitness.wearableservice.model;

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
import java.util.Map;

/**
 * Raw event received from a wearable device.
 * Persisted before being published to Kafka — Kafka is the transport,
 * MongoDB is the source of truth for audit and replay.
 */
@Document(collection = "wearable_events")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@CompoundIndexes({
    // Efficient session window queries: userId + eventType + time range
    @CompoundIndex(name = "session_query",
                   def = "{'userId':1,'eventType':1,'timestamp':-1}"),
    // Manual sync and aggregation: userId + unprocessed + timestamp
    @CompoundIndex(name = "unprocessed_query",
                   def = "{'userId':1,'processed':1,'timestamp':1}")
})
public class WearableEvent {

    @Id
    private String id;

    @Indexed
    private String userId;            // Keycloak sub — matches X-User-ID

    private DeviceType deviceType;
    private EventType  eventType;

    /**
     * Activity type label (RUNNING, CYCLING, etc.) provided by the device for
     * WORKOUT_START / WORKOUT_END events. Mapped to ActivityService's ActivityType enum.
     */
    private String activityType;

    private LocalDateTime timestamp;  // event time reported by device

    // ── Metric payloads (nullable per eventType) ──────────────────────────────
    private Integer heartRate;        // bpm
    private Integer steps;            // cumulative or delta
    private Integer caloriesBurned;
    private Double  distanceKm;
    private Double  latitude;
    private Double  longitude;
    private Double  altitude;         // meters

    /** Vendor-specific raw payload — preserved for debugging and future extensions. */
    private Map<String, Object> rawData;

    /**
     * Set to true once this event has been rolled up into an Activity.
     * WORKOUT_START/END events and all events in a session window are marked
     * processed together after a successful activity creation.
     */
    @Builder.Default
    private Boolean processed = false;

    @CreatedDate
    private LocalDateTime receivedAt;  // when wearableservice received it
}
