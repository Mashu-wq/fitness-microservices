package com.fitness.wearableservice.controller;

import com.fitness.wearableservice.dto.SyncResponse;
import com.fitness.wearableservice.dto.WearableEventRequest;
import com.fitness.wearableservice.dto.WearableEventResponse;
import com.fitness.wearableservice.model.DeviceType;
import com.fitness.wearableservice.model.EventType;
import com.fitness.wearableservice.service.WearableEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wearables")
@RequiredArgsConstructor
public class WearableController {

    private final WearableEventService service;

    /**
     * POST /api/wearables/events
     *
     * Primary ingest endpoint. Called by device SDKs or integration gateways.
     * The event is persisted and published to Kafka immediately.
     *
     * WORKOUT_END events trigger automatic workout aggregation → Activity creation
     * via the Kafka consumer (asynchronous — response does not wait for it).
     */
    @PostMapping("/events")
    public ResponseEntity<WearableEventResponse> ingestEvent(
            @RequestHeader("X-User-ID") String userId,
            @Valid @RequestBody WearableEventRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.ingest(userId, request));
    }

    /**
     * GET /api/wearables/events
     *
     * Returns all wearable events for the authenticated user, newest first.
     * Shows processed=false for events not yet rolled up into an Activity.
     */
    @GetMapping("/events")
    public ResponseEntity<List<WearableEventResponse>> getEvents(
            @RequestHeader("X-User-ID") String userId) {
        return ResponseEntity.ok(service.getEventsForUser(userId));
    }

    /**
     * POST /api/wearables/sync
     *
     * Manual sync: aggregates ALL unprocessed events into one Activity immediately.
     * Use this for devices that don't emit WORKOUT_START/END events, or to force
     * a sync before WORKOUT_END arrives.
     */
    @PostMapping("/sync")
    public ResponseEntity<SyncResponse> sync(
            @RequestHeader("X-User-ID") String userId) {
        return ResponseEntity.ok(service.manualSync(userId));
    }

    /**
     * GET /api/wearables/pending
     *
     * Returns count of unprocessed events for the user.
     * Useful for device dashboards to show sync status.
     */
    @GetMapping("/pending")
    public ResponseEntity<Map<String, Long>> pendingCount(
            @RequestHeader("X-User-ID") String userId) {
        return ResponseEntity.ok(Map.of("pendingEvents", service.pendingEventCount(userId)));
    }

    /**
     * GET /api/wearables/devices
     * Returns supported device types.
     */
    @GetMapping("/devices")
    public ResponseEntity<List<String>> getSupportedDevices() {
        return ResponseEntity.ok(Arrays.stream(DeviceType.values()).map(Enum::name).toList());
    }

    /**
     * GET /api/wearables/event-types
     * Returns supported event types with descriptions.
     */
    @GetMapping("/event-types")
    public ResponseEntity<List<Map<String, String>>> getEventTypes() {
        List<Map<String, String>> types = List.of(
            Map.of("type", EventType.WORKOUT_START.name(), "description", "Marks the start of a tracked workout session"),
            Map.of("type", EventType.WORKOUT_END.name(),   "description", "Marks the end — triggers automatic activity creation"),
            Map.of("type", EventType.HEART_RATE.name(),    "description", "Real-time heart rate reading (bpm)"),
            Map.of("type", EventType.STEPS.name(),         "description", "Step count snapshot"),
            Map.of("type", EventType.CALORIES.name(),      "description", "Calorie burn snapshot"),
            Map.of("type", EventType.GPS_TRACK.name(),     "description", "GPS location point (latitude/longitude/altitude)"),
            Map.of("type", EventType.SLEEP.name(),         "description", "Sleep stage or duration data")
        );
        return ResponseEntity.ok(types);
    }
}
