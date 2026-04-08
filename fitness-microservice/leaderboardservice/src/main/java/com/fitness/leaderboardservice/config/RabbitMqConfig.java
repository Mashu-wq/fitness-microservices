package com.fitness.leaderboardservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    // ── Inbound: activity events from fitness.exchange ─────────────────────────
    // fitness.exchange is declared by activityservice (idempotent re-declaration is fine).
    // leaderboard.activity.queue binds with routing key "activity.tracking" —
    // DirectExchange fan-out alongside activity.queue and goal.activity.queue.

    private static final String FITNESS_EXCHANGE        = "fitness.exchange";
    private static final String ACTIVITY_ROUTING_KEY    = "activity.tracking";
    private static final String LEADERBOARD_QUEUE       = "leaderboard.activity.queue";
    private static final String LEADERBOARD_DLX         = "leaderboard.activity.dlx";
    private static final String LEADERBOARD_DLQ         = "leaderboard.activity.dlq";

    @Bean
    public DirectExchange leaderboardDlx() {
        return new DirectExchange(LEADERBOARD_DLX);
    }

    @Bean
    public Queue leaderboardDlq() {
        return QueueBuilder.durable(LEADERBOARD_DLQ).build();
    }

    @Bean
    public Binding leaderboardDlqBinding() {
        return BindingBuilder.bind(leaderboardDlq()).to(leaderboardDlx()).with(LEADERBOARD_QUEUE);
    }

    @Bean
    public Queue leaderboardActivityQueue() {
        return QueueBuilder.durable(LEADERBOARD_QUEUE)
                .withArgument("x-dead-letter-exchange", LEADERBOARD_DLX)
                .withArgument("x-dead-letter-routing-key", LEADERBOARD_QUEUE)
                .build();
    }

    @Bean
    public DirectExchange fitnessExchange() {
        return new DirectExchange(FITNESS_EXCHANGE, true, false);
    }

    @Bean
    public Binding leaderboardActivityBinding() {
        return BindingBuilder.bind(leaderboardActivityQueue())
                .to(fitnessExchange())
                .with(ACTIVITY_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
