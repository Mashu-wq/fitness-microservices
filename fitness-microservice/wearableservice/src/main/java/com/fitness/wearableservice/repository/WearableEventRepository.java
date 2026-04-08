package com.fitness.wearableservice.repository;

import com.fitness.wearableservice.model.EventType;
import com.fitness.wearableservice.model.WearableEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WearableEventRepository extends MongoRepository<WearableEvent, String> {

    List<WearableEvent> findByUserIdOrderByTimestampDesc(String userId);

    List<WearableEvent> findByUserIdAndEventTypeOrderByTimestampDesc(String userId, EventType eventType);

    /** Used to find the most recent WORKOUT_START that has not yet been rolled up. */
    Optional<WearableEvent> findTopByUserIdAndEventTypeAndProcessedFalseOrderByTimestampDesc(
            String userId, EventType eventType);

    /** Fetch all events in a session window for aggregation. */
    List<WearableEvent> findByUserIdAndTimestampBetweenOrderByTimestampAsc(
            String userId, LocalDateTime from, LocalDateTime to);

    /** All unprocessed events for a user — used by manual sync. */
    List<WearableEvent> findByUserIdAndProcessedFalseOrderByTimestampAsc(String userId);

    long countByUserIdAndProcessedFalse(String userId);
}
