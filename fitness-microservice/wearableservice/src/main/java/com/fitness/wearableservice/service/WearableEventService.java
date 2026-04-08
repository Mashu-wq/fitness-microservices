package com.fitness.wearableservice.service;

import com.fitness.wearableservice.dto.SyncResponse;
import com.fitness.wearableservice.dto.WearableEventRequest;
import com.fitness.wearableservice.dto.WearableEventResponse;
import com.fitness.wearableservice.model.WearableEvent;
import com.fitness.wearableservice.repository.WearableEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WearableEventService {

    private final WearableEventRepository repository;
    private final WearableKafkaProducer kafkaProducer;
    private final WorkoutAggregationService aggregationService;

    /**
     * Primary ingest path:
     * 1. Validate and persist the event to MongoDB
     * 2. Publish to Kafka asynchronously (non-blocking; failures logged but not propagated)
     */
    public WearableEventResponse ingest(String userId, WearableEventRequest req) {
        WearableEvent event = WearableEvent.builder()
                .userId(userId)
                .deviceType(req.getDeviceType())
                .eventType(req.getEventType())
                .activityType(req.getActivityType())
                .timestamp(req.getTimestamp() != null ? req.getTimestamp() : LocalDateTime.now())
                .heartRate(req.getHeartRate())
                .steps(req.getSteps())
                .caloriesBurned(req.getCaloriesBurned())
                .distanceKm(req.getDistanceKm())
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .altitude(req.getAltitude())
                .rawData(req.getRawData())
                .build();

        WearableEvent saved = repository.save(event);
        log.info("Saved wearable event id={} userId={} type={}", saved.getId(), userId, saved.getEventType());

        // Fire-and-forget to Kafka (failures handled by producer's whenComplete callback)
        kafkaProducer.publish(saved);

        return mapToResponse(saved);
    }

    public List<WearableEventResponse> getEventsForUser(String userId) {
        return repository.findByUserIdOrderByTimestampDesc(userId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public SyncResponse manualSync(String userId) {
        return aggregationService.manualSync(userId);
    }

    public long pendingEventCount(String userId) {
        return repository.countByUserIdAndProcessedFalse(userId);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private WearableEventResponse mapToResponse(WearableEvent e) {
        return WearableEventResponse.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .deviceType(e.getDeviceType())
                .eventType(e.getEventType())
                .activityType(e.getActivityType())
                .timestamp(e.getTimestamp())
                .heartRate(e.getHeartRate())
                .steps(e.getSteps())
                .caloriesBurned(e.getCaloriesBurned())
                .distanceKm(e.getDistanceKm())
                .processed(e.getProcessed())
                .receivedAt(e.getReceivedAt())
                .build();
    }
}
