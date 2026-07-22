package com.cpallas.expenses.reporting.service;

import com.cpallas.expenses.reporting.config.ReportingRabbitConfig;
import com.cpallas.expenses.reporting.contract.MonthlyReportRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MonthlyReportPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(MonthlyReportRequested event) {
        rabbitTemplate.convertAndSend(ReportingRabbitConfig.EXCHANGE, ReportingRabbitConfig.REQUEST_ROUTING_KEY, event);
    }
}
