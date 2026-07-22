package com.cpallas.expenses.reporting.contract;

import java.time.ZonedDateTime;
import java.util.UUID;

public record MonthlyReportRequested(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Long chatId,
        ReportPeriod period,
        ZonedDateTime requestedAt
) {
    public static MonthlyReportRequested of(UUID eventId, Long chatId, ReportPeriod period) {
        return new MonthlyReportRequested(eventId, "MonthlyReportRequested", 1, chatId, period, ZonedDateTime.now());
    }
}
