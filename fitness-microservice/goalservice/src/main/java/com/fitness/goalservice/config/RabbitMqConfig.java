package com.fitness.goalservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    // ── Inbound: consume activity events from fitness.exchange ─────────────────
    // fitness.exchange already declared by activityservice; we only declare our queue.
    // Both activity.queue (AIService) and goal.activity.queue (GoalService) bind with
    // the same routing key — DirectExchange fan-out.

    public static final String FITNESS_EXCHANGE = "fitness.exchange";
    public static final String ACTIVITY_ROUTING_KEY = "activity.tracking";

    public static final String GOAL_ACTIVITY_QUEUE = "goal.activity.queue";
    public static final String GOAL_ACTIVITY_DLX = "goal.activity.dlx";
    public static final String GOAL_ACTIVITY_DLQ = "goal.activity.dlq";

    // ── Outbound: publish milestone events for AIService ──────────────────────
    public static final String GOAL_PROGRESS_EXCHANGE = "goal.progress.exchange";
    public static final String GOAL_PROGRESS_ROUTING_KEY = "goal.progress";

    public static final String GOAL_AI_QUEUE = "goal.ai.queue";
    public static final String GOAL_AI_DLX = "goal.ai.dlx";
    public static final String GOAL_AI_DLQ = "goal.ai.dlq";

    // ── Dead-letter exchanges ──────────────────────────────────────────────────

    @Bean
    public DirectExchange goalActivityDlx() {
        return new DirectExchange(GOAL_ACTIVITY_DLX);
    }

    @Bean
    public Queue goalActivityDlq() {
        return QueueBuilder.durable(GOAL_ACTIVITY_DLQ).build();
    }

    @Bean
    public Binding goalActivityDlqBinding() {
        return BindingBuilder.bind(goalActivityDlq()).to(goalActivityDlx()).with(GOAL_ACTIVITY_QUEUE);
    }

    // ── Inbound queue (with DLX argument) ─────────────────────────────────────

    @Bean
    public Queue goalActivityQueue() {
        return QueueBuilder.durable(GOAL_ACTIVITY_QUEUE)
                .withArgument("x-dead-letter-exchange", GOAL_ACTIVITY_DLX)
                .withArgument("x-dead-letter-routing-key", GOAL_ACTIVITY_QUEUE)
                .build();
    }

    @Bean
    public DirectExchange fitnessExchange() {
        // Declare as durable to survive broker restarts; activityservice does the same
        return new DirectExchange(FITNESS_EXCHANGE, true, false);
    }

    @Bean
    public Binding goalActivityBinding() {
        return BindingBuilder.bind(goalActivityQueue()).to(fitnessExchange()).with(ACTIVITY_ROUTING_KEY);
    }

    // ── Outbound exchange (milestone events → AIService) ──────────────────────

    @Bean
    public DirectExchange goalProgressExchange() {
        return new DirectExchange(GOAL_PROGRESS_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange goalAiDlx() {
        return new DirectExchange(GOAL_AI_DLX);
    }

    @Bean
    public Queue goalAiDlq() {
        return QueueBuilder.durable(GOAL_AI_DLQ).build();
    }

    @Bean
    public Binding goalAiDlqBinding() {
        return BindingBuilder.bind(goalAiDlq()).to(goalAiDlx()).with(GOAL_AI_QUEUE);
    }

    @Bean
    public Queue goalAiQueue() {
        return QueueBuilder.durable(GOAL_AI_QUEUE)
                .withArgument("x-dead-letter-exchange", GOAL_AI_DLX)
                .withArgument("x-dead-letter-routing-key", GOAL_AI_QUEUE)
                .build();
    }

    @Bean
    public Binding goalAiBinding() {
        return BindingBuilder.bind(goalAiQueue()).to(goalProgressExchange()).with(GOAL_PROGRESS_ROUTING_KEY);
    }

    // ── Message converter & template ──────────────────────────────────────────

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
