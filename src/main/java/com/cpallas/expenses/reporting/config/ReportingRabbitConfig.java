package com.cpallas.expenses.reporting.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class ReportingRabbitConfig {

    public static final String EXCHANGE = "expenses.events";
    public static final String REPORT_READY_QUEUE = "tg-bot.monthly-report.ready";
    public static final String REPORT_READY_DLQ = "tg-bot.monthly-report.ready.dlq";
    public static final String REQUEST_ROUTING_KEY = "analytics.monthly-report.requested";
    public static final String READY_ROUTING_KEY = "tg-bot.monthly-report.ready";

    @Bean
    public TopicExchange expensesEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue monthlyReportReadyQueue() {
        return new Queue(REPORT_READY_QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", EXCHANGE,
                "x-dead-letter-routing-key", REPORT_READY_DLQ
        ));
    }

    @Bean
    public Queue monthlyReportReadyDeadLetterQueue() {
        return new Queue(REPORT_READY_DLQ, true);
    }

    @Bean
    public Binding monthlyReportReadyBinding(Queue monthlyReportReadyQueue, TopicExchange expensesEventsExchange) {
        return BindingBuilder.bind(monthlyReportReadyQueue).to(expensesEventsExchange).with(READY_ROUTING_KEY);
    }

    @Bean
    public Binding monthlyReportReadyDeadLetterBinding(Queue monthlyReportReadyDeadLetterQueue, TopicExchange expensesEventsExchange) {
        return BindingBuilder.bind(monthlyReportReadyDeadLetterQueue).to(expensesEventsExchange).with(REPORT_READY_DLQ);
    }

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
