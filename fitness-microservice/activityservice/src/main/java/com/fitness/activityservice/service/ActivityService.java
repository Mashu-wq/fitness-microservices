package com.fitness.activityservice.service;

import com.fitness.activityservice.dto.ActivityRequest;
import com.fitness.activityservice.dto.ActivityResponse;
import com.fitness.activityservice.model.Activity;
import com.fitness.activityservice.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final UserValidationService userValidationService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange.name}")
    private String exchange;

    @Value("${rabbitmq.routing.key}")
    private String routingKey;

    // FIX: userId is now an explicit parameter injected from the gateway header
    public ActivityResponse trackActivity(ActivityRequest request, String userId) {

        boolean isValidUser = userValidationService.validateUser(userId);
        if (!isValidUser) {
            throw new RuntimeException("Invalid or non-existent user: " + userId);
        }

        Activity activity = Activity.builder()
                .userId(userId)
                .type(request.getType())
                .duration(request.getDuration())
                .caloriesBurned(request.getCaloriesBurned())
                .startTime(request.getStartTime())
                .additionalMetrics(request.getAdditionalMetrics()) // FIX: additionalMetrices → additionalMetrics
                .build();

        Activity savedActivity = activityRepository.save(activity);

        // Publish to RabbitMQ for async AI processing (fire-and-forget)
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, savedActivity);
        } catch (Exception e) {
            log.error("Failed to publish activity {} to RabbitMQ: {}", savedActivity.getId(), e.getMessage());
        }

        return mapToActivityResponse(savedActivity);
    }

    public List<ActivityResponse> getUserActivities(String userId) {
        return activityRepository.findByUserId(userId)
                .stream()
                .map(this::mapToActivityResponse)
                .toList();
    }

    public ActivityResponse getActivityById(String activityId) {
        return activityRepository.findById(activityId)
                .map(this::mapToActivityResponse)
                .orElseThrow(() -> new RuntimeException("Activity not found with id: " + activityId));
    }

    private ActivityResponse mapToActivityResponse(Activity activity) {
        ActivityResponse response = new ActivityResponse();
        response.setId(activity.getId());
        response.setUserId(activity.getUserId());
        response.setType(activity.getType());
        response.setDuration(activity.getDuration());
        response.setCaloriesBurned(activity.getCaloriesBurned());
        response.setStartTime(activity.getStartTime());
        response.setAdditionalMetrics(activity.getAdditionalMetrics()); // FIX: typo
        response.setCreatedAt(activity.getCreatedAt());
        response.setUpdatedAt(activity.getUpdatedAt());               // FIX: typo
        return response;
    }
}
