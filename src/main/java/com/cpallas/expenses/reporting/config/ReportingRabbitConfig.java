package com.cpallas.expenses.reporting.config;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class ReportingRabbitConfig {

    public static final String EXCHANGE = "expenses.events";

    public static final String MONTHLY_REQUEST_ROUTING_KEY = "analytics.monthly-report.requested";
    public static final String WEEKLY_REQUEST_ROUTING_KEY = "analytics.weekly-report.requested";
    public static final String EXPENSE_RECORDED_ROUTING_KEY = "analytics.expense.recorded";

    public static final String MONTHLY_READY_QUEUE = "tg-bot.monthly-report.ready";
    public static final String WEEKLY_READY_QUEUE = "tg-bot.weekly-report.ready";
    public static final String EXTREME_EXPENSE_QUEUE = "tg-bot.extreme-expense.detected";

    @Bean
    public TopicExchange expensesEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Declarables analyticsEventQueues(TopicExchange expensesEventsExchange) {
        List<org.springframework.amqp.core.Declarable> declarations = new ArrayList<>();
        declareQueue(declarations, expensesEventsExchange, MONTHLY_REQUEST_ROUTING_KEY);
        declareQueue(declarations, expensesEventsExchange, WEEKLY_REQUEST_ROUTING_KEY);
        declareQueue(declarations, expensesEventsExchange, EXPENSE_RECORDED_ROUTING_KEY);
        declareQueue(declarations, expensesEventsExchange, MONTHLY_READY_QUEUE);
        declareQueue(declarations, expensesEventsExchange, WEEKLY_READY_QUEUE);
        declareQueue(declarations, expensesEventsExchange, EXTREME_EXPENSE_QUEUE);
        return new Declarables(declarations);
    }

    private void declareQueue(List<org.springframework.amqp.core.Declarable> declarations,
                              TopicExchange exchange,
                              String routingKey) {
        String deadLetterRoutingKey = routingKey + ".dlq";
        Queue queue = QueueBuilder.durable(routingKey)
                .deadLetterExchange(EXCHANGE)
                .deadLetterRoutingKey(deadLetterRoutingKey)
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(deadLetterRoutingKey).build();
        declarations.add(queue);
        declarations.add(deadLetterQueue);
        declarations.add(BindingBuilder.bind(queue).to(exchange).with(routingKey));
        declarations.add(BindingBuilder.bind(deadLetterQueue).to(exchange).with(deadLetterRoutingKey));
    }

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
