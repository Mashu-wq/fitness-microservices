package com.fitness.wearableservice.service;

import com.fitness.wearableservice.dto.ActivityRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Calls ActivityService to create an Activity from aggregated wearable data.
 *
 * By going through ActivityService rather than publishing directly to RabbitMQ,
 * we ensure:
 *   1. The activity is persisted in MongoDB (single source of truth)
 *   2. ActivityService handles the RabbitMQ publish to fitness.exchange
 *   3. All downstream services (AI, Goal, Leaderboard) receive it via their existing queues
 */
@Service
@Slf4j
public class ActivityPublisher {

    private final WebClient webClient;

    public ActivityPublisher(WebClient.Builder builder,
                             @Value("${services.activity-service.url:lb://ACTIVITY-SERVICE}") String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    /**
     * POSTs the aggregated activity to ActivityService.
     * Blocks up to 10 seconds — the caller (Kafka consumer) is synchronous.
     *
     * @throws RuntimeException if ActivityService returns an error or times out
     */
    public void publish(String userId, ActivityRequest request) {
        log.info("Publishing activity to ActivityService: userId={} type={} duration={}min calories={}",
                userId, request.getType(), request.getDuration(), request.getCaloriesBurned());

        webClient.post()
                .uri("/api/activities")
                .header("X-User-ID", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class).flatMap(body -> {
                            log.error("ActivityService returned {} for userId={}: {}",
                                    response.statusCode(), userId, body);
                            return Mono.error(new RuntimeException(
                                    "ActivityService error " + response.statusCode() + ": " + body));
                        }))
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(10))
                .block();

        log.info("Activity successfully created in ActivityService for userId={}", userId);
    }
}
