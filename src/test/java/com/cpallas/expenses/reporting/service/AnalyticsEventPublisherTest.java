package com.cpallas.expenses.reporting.service;

import com.cpallas.expenses.reporting.contract.ExpenseRecorded;
import com.cpallas.expenses.storage.jpa.OutboxEventJpa;
import com.cpallas.expenses.storage.repo.OutboxEventRepo;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AnalyticsEventPublisherTest {

    @Mock
    private OutboxEventRepo outboxEventRepo;

    @Test
    void storesExpenseLookupKeysInOutboxWithoutExpenseData() throws Exception {
        var objectMapper = JsonMapper.builder().findAndAddModules().build();
        var publisher = new AnalyticsEventPublisher(outboxEventRepo, objectMapper);
        UUID expenseId = UUID.randomUUID();

        publisher.publish(ExpenseRecorded.of(expenseId, 42L));

        ArgumentCaptor<OutboxEventJpa> outbox = ArgumentCaptor.forClass(OutboxEventJpa.class);
        verify(outboxEventRepo).save(outbox.capture());
        var payload = objectMapper.readTree(outbox.getValue().getPayload());
        assertThat(payload.get("expenseId").asText()).isEqualTo(expenseId.toString());
        assertThat(payload.get("chatId").asLong()).isEqualTo(42L);
        assertThat(payload.has("amount")).isFalse();
        assertThat(payload.has("categoryName")).isFalse();
    }
}
