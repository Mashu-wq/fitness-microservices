package com.fitness.wearableservice.config;

import com.fitness.wearableservice.model.WearableEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;

@Configuration
public class KafkaConfig {

    public static final String WEARABLE_TOPIC = "wearable.raw.events";
    public static final String WEARABLE_DLT   = "wearable.raw.events.dlt";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ── Topic declarations ─────────────────────────────────────────────────────

    /** Main ingest topic — 3 partitions keyed by userId for ordering per device. */
    @Bean
    public NewTopic wearableTopic() {
        return TopicBuilder.name(WEARABLE_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /** Dead-letter topic — failed events park here after retries are exhausted. */
    @Bean
    public NewTopic wearableDlt() {
        return TopicBuilder.name(WEARABLE_DLT)
                .partitions(1)
                .replicas(1)
                .build();
    }

    // ── Producer ───────────────────────────────────────────────────────────────

    @Bean
    public ProducerFactory<String, WearableEvent> producerFactory() {
        return new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class,
                // Idempotent producer: exactly-once delivery within a partition
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.RETRIES_CONFIG, 3
        ));
    }

    @Bean
    public KafkaTemplate<String, WearableEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ── Consumer ───────────────────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, WearableEvent> consumerFactory() {
        JsonDeserializer<WearableEvent> deserializer = new JsonDeserializer<>(WearableEvent.class, false);
        deserializer.addTrustedPackages("com.fitness.wearableservice.model");

        return new DefaultKafkaConsumerFactory<>(
                Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                        ConsumerConfig.GROUP_ID_CONFIG, "wearable-processor",
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                        // Manual ack so we only commit after successful processing
                        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false
                ),
                new StringDeserializer(),
                deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, WearableEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, WearableEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3); // one thread per partition
        return factory;
    }
}
