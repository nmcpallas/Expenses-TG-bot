package com.cpallas.expenses.reporting.contract;

import java.time.ZonedDateTime;
import java.util.UUID;

public record ExpenseRecorded(
        UUID eventId,
        String eventType,
        int schemaVersion,
        UUID expenseId,
        Long chatId,
        ZonedDateTime recordedAt
) {
    public static ExpenseRecorded of(UUID expenseId, Long chatId) {
        return new ExpenseRecorded(
                UUID.randomUUID(),
                "ExpenseRecorded",
                1,
                expenseId,
                chatId,
                ZonedDateTime.now()
        );
    }
}
