package com.cpallas.expenses.reporting.service;

import com.cpallas.expenses.reporting.contract.ExtremeExpenseDetected;
import com.cpallas.expenses.storage.repo.ExtremeExpenseDeliveryRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtremeExpenseListenerTest {

    @Mock
    private ExtremeExpenseDeliveryRepo deliveryRepo;
    @Mock
    private TelegramClient telegramClient;

    @Test
    void sendsDetectedExpenseAndMarksEventDelivered() throws Exception {
        ExtremeExpenseDetected event = new ExtremeExpenseDetected(
                UUID.randomUUID(),
                "ExtremeExpenseDetected",
                1,
                UUID.randomUUID(),
                42L,
                "Кафе",
                new BigDecimal("1500"),
                new BigDecimal("400"),
                new BigDecimal("3.8"),
                ZonedDateTime.now()
        );
        when(deliveryRepo.claim(eq(event.eventId()), eq(event.expenseId()), eq(42L), any()))
                .thenReturn(1);

        new ExtremeExpenseListener(deliveryRepo, telegramClient).receive(event);

        var message = org.mockito.ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(message.capture());
        assertThat(message.getValue().getText())
                .contains("Необычная трата")
                .contains("1500 · Кафе")
                .contains("3.8 раза");
        verify(deliveryRepo).markDelivered(eq(event.eventId()), any());
    }
}
