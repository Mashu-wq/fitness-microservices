package com.fitness.wearableservice.service;

import com.fitness.wearableservice.config.KafkaConfig;
import com.fitness.wearableservice.model.WearableEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class WearableKafkaProducer {

    private final KafkaTemplate<String, WearableEvent> kafkaTemplate;

    /**
     * Publishes a wearable event to the Kafka topic.
     * The message key is the userId so all events for the same user land
     * on the same partition — preserving per-user ordering.
     */
    public void publish(WearableEvent event) {
        CompletableFuture<SendResult<String, WearableEvent>> future =
                kafkaTemplate.send(KafkaConfig.WEARABLE_TOPIC, event.getUserId(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish wearable event id={} userId={}: {}",
                        event.getId(), event.getUserId(), ex.getMessage(), ex);
            } else {
                log.debug("Published wearable event id={} to partition={} offset={}",
                        event.getId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
