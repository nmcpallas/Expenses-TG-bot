package com.cpallas.expenses.reporting.service;

import com.cpallas.expenses.reporting.config.ReportingRabbitConfig;
import com.cpallas.expenses.storage.repo.OutboxEventRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventDispatcher {

    private final OutboxEventRepo outboxEventRepo;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelayString = "${expense.outbox.fixed-delay-ms:1000}")
    public void publishPendingEvents() {
        for (var event : outboxEventRepo.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            try {
                Message message = MessageBuilder
                        .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                        .setMessageId(event.getEventId().toString())
                        .build();
                rabbitTemplate.invoke(operations -> {
                    operations.send(
                            ReportingRabbitConfig.EXCHANGE,
                            event.getRoutingKey(),
                            message
                    );
                    operations.waitForConfirmsOrDie(5_000);
                    return null;
                });
                outboxEventRepo.markPublished(event.getId(), ZonedDateTime.now());
            } catch (Exception exception) {
                outboxEventRepo.markFailedAttempt(event.getId());
                log.error(
                        "Unable to publish outbox event: eventId={}, routingKey={}",
                        event.getEventId(),
                        event.getRoutingKey(),
                        exception
                );
            }
        }
    }
}
