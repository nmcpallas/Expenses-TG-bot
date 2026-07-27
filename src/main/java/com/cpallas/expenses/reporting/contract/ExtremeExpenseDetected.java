package com.cpallas.expenses.reporting.contract;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

public record ExtremeExpenseDetected(
        UUID eventId,
        String eventType,
        int schemaVersion,
        UUID expenseId,
        Long chatId,
        String categoryName,
        BigDecimal amount,
        BigDecimal usualAmount,
        BigDecimal multiplier,
        ZonedDateTime detectedAt
) {
}
