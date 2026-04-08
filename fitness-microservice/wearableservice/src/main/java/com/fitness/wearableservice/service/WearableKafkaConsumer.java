package com.fitness.wearableservice.service;

import com.fitness.wearableservice.config.KafkaConfig;
import com.fitness.wearableservice.model.EventType;
import com.fitness.wearableservice.model.WearableEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * Consumes wearable events from Kafka and triggers workout aggregation on WORKOUT_END.
 *
 * Retry policy: 3 attempts with exponential backoff (1s → 2s → 4s).
 * After 3 failures the message is routed to the DLT (wearable.raw.events.dlt)
 * rather than blocking the partition.
 *
 * Non-WORKOUT_END events are acknowledged immediately (already persisted in MongoDB
 * by the REST endpoint before Kafka publish).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WearableKafkaConsumer {

    private final WorkoutAggregationService aggregationService;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = ".dlt",
            autoCreateTopics = "true"
    )
    @KafkaListener(
            topics = KafkaConfig.WEARABLE_TOPIC,
            groupId = "wearable-processor",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(WearableEvent event,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Kafka event received: id={} userId={} type={} partition={} offset={}",
                event.getId(), event.getUserId(), event.getEventType(), partition, offset);

        if (event.getEventType() == EventType.WORKOUT_END) {
            // Aggregate the workout session and create an Activity
            boolean created = aggregationService.aggregateWorkoutSession(event);
            if (!created) {
                log.warn("No activity created for WORKOUT_END event id={} (no matching WORKOUT_START)",
                        event.getId());
            }
        } else {
            // HEART_RATE, STEPS, GPS_TRACK, SLEEP, CALORIES, WORKOUT_START
            // These are already persisted — just log for observability
            log.debug("Wearable event acknowledged: type={} userId={}", event.getEventType(), event.getUserId());
        }
    }

    /**
     * Handles messages that exhausted all retries.
     * Logs the failure for alerting — DLT message is preserved for manual replay.
     */
    @DltHandler
    public void handleDlt(WearableEvent event,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("DEAD LETTER: wearable event id={} userId={} type={} sent to DLT topic={}",
                event.getId(), event.getUserId(), event.getEventType(), topic);
    }
}
