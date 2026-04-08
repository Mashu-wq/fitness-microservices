package com.fitness.aiservice.service;

import com.fitness.aiservice.model.GoalProgressEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GoalProgressEventListener {

    private final GoalAIService goalAIService;

    @RabbitListener(queues = "goal.ai.queue")
    public void onGoalProgressEvent(GoalProgressEvent event) {
        log.info("Received goal milestone event: goalId={}, milestone={}%, user={}",
                event.getGoalId(),
                event.getMilestone() != null ? event.getMilestone().intValue() : "?",
                event.getUserId());
        try {
            goalAIService.generateGoalRecommendation(event);
        } catch (Exception e) {
            log.error("Failed to process goal milestone event for goalId {}: {}",
                    event.getGoalId(), e.getMessage(), e);
            // Re-throw so RabbitMQ's DLX moves the message to goal.ai.dlq
            throw e;
        }
    }
}
