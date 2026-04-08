package com.fitness.goalservice.service;

import com.fitness.goalservice.model.Activity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ActivityEventListener {

    private final GoalProgressService goalProgressService;

    /**
     * Consumes activity events from the goal-specific binding of fitness.exchange.
     * The DirectExchange fan-out is achieved by binding both activity.queue (AIService)
     * and goal.activity.queue (this service) to the same routing key "activity.tracking".
     */
    @RabbitListener(queues = "goal.activity.queue")
    public void onActivityEvent(Activity activity) {
        log.info("Received activity event: id={}, user={}, type={}",
                activity.getId(), activity.getUserId(), activity.getType());
        try {
            goalProgressService.processActivity(activity);
        } catch (Exception e) {
            log.error("Failed to process activity {} for goal progress: {}",
                    activity.getId(), e.getMessage(), e);
            // Re-throw so RabbitMQ's DLX moves the message to goal.activity.dlq
            throw e;
        }
    }
}
