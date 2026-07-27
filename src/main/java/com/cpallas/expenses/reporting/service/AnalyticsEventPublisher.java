package com.cpallas.expenses.reporting.service;

import com.cpallas.expenses.reporting.config.ReportingRabbitConfig;
import com.cpallas.expenses.reporting.contract.ExpenseRecorded;
import com.cpallas.expenses.reporting.contract.MonthlyReportRequested;
import com.cpallas.expenses.reporting.contract.WeeklyReportRequested;
import com.cpallas.expenses.storage.jpa.OutboxEventJpa;
import com.cpallas.expenses.storage.repo.OutboxEventRepo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyticsEventPublisher {

    private final OutboxEventRepo outboxEventRepo;
    private final ObjectMapper objectMapper;

    public void publish(MonthlyReportRequested event) {
        enqueue(event.eventId(), ReportingRabbitConfig.MONTHLY_REQUEST_ROUTING_KEY, event);
    }

    public void publish(WeeklyReportRequested event) {
        enqueue(event.eventId(), ReportingRabbitConfig.WEEKLY_REQUEST_ROUTING_KEY, event);
    }

    public void publish(ExpenseRecorded event) {
        enqueue(event.eventId(), ReportingRabbitConfig.EXPENSE_RECORDED_ROUTING_KEY, event);
    }

    private void enqueue(UUID eventId, String routingKey, Object event) {
        OutboxEventJpa outbox = new OutboxEventJpa();
        outbox.setId(UUID.randomUUID());
        outbox.setEventId(eventId);
        outbox.setRoutingKey(routingKey);
        outbox.setPayload(toJson(event));
        outbox.setCreatedAt(ZonedDateTime.now());
        outbox.setAttemptCount(0);
        outboxEventRepo.save(outbox);
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize analytics event", exception);
        }
    }
}
