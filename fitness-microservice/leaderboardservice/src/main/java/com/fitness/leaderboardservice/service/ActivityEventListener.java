package com.fitness.leaderboardservice.service;

import com.fitness.leaderboardservice.model.Activity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ActivityEventListener {

    private final ScoreUpdateService scoreUpdateService;

    /**
     * Consumes activity events from the leaderboard-specific binding on fitness.exchange.
     * DirectExchange fan-out: activity.queue (AIService), goal.activity.queue (GoalService),
     * and leaderboard.activity.queue (this service) all receive a copy of every event.
     */
    @RabbitListener(queues = "leaderboard.activity.queue")
    public void onActivityEvent(Activity activity) {
        log.info("Received activity event: id={}, user={}, type={}",
                activity.getId(), activity.getUserId(), activity.getType());
        try {
            scoreUpdateService.processActivity(activity);
        } catch (Exception e) {
            log.error("Failed to update leaderboard for activity {}: {}",
                    activity.getId(), e.getMessage(), e);
            // Re-throw so DLX captures the message in leaderboard.activity.dlq
            throw e;
        }
    }
}
