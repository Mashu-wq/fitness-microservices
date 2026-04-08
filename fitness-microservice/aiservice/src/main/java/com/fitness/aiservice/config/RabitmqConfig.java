package com.fitness.aiservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabitmqConfig {

    private static final String DLQ_EXCHANGE = "fitness.dlx";
    private static final String DLQ_NAME     = "activity.queue.dlq";

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLQ_EXCHANGE);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ_NAME).build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with("activity.tracking");
    }

    @Bean
    public Queue activityQueue() {
        return QueueBuilder.durable("activity.queue")
                .withArgument("x-dead-letter-exchange", DLQ_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "activity.tracking")
                .build();
    }

    @Bean
    public DirectExchange activityExchange() {
        return new DirectExchange("fitness.exchange");
    }

    @Bean
    public Binding activityBinding(Queue activityQueue, DirectExchange activityExchange) {
        return BindingBuilder.bind(activityQueue).to(activityExchange).with("activity.tracking");
    }

    // ── Goal milestone events: goal.progress.exchange → goal.ai.queue ─────────

    private static final String GOAL_PROGRESS_EXCHANGE  = "goal.progress.exchange";
    private static final String GOAL_PROGRESS_ROUTING_KEY = "goal.progress";
    private static final String GOAL_AI_QUEUE  = "goal.ai.queue";
    private static final String GOAL_AI_DLX    = "goal.ai.dlx";
    private static final String GOAL_AI_DLQ    = "goal.ai.dlq";

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
    public Binding goalAiDlqBinding(Queue goalAiDlq, DirectExchange goalAiDlx) {
        return BindingBuilder.bind(goalAiDlq).to(goalAiDlx).with(GOAL_AI_QUEUE);
    }

    @Bean
    public Queue goalAiQueue() {
        return QueueBuilder.durable(GOAL_AI_QUEUE)
                .withArgument("x-dead-letter-exchange", GOAL_AI_DLX)
                .withArgument("x-dead-letter-routing-key", GOAL_AI_QUEUE)
                .build();
    }

    @Bean
    public Binding goalAiBinding(Queue goalAiQueue, DirectExchange goalProgressExchange) {
        return BindingBuilder.bind(goalAiQueue).to(goalProgressExchange).with(GOAL_PROGRESS_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
