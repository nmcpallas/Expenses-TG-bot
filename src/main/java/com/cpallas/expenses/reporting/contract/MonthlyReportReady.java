package com.cpallas.expenses.reporting.contract;

import java.time.ZonedDateTime;
import java.util.UUID;

public record MonthlyReportReady(
        UUID eventId,
        String eventType,
        int schemaVersion,
        UUID reportId,
        Long chatId,
        ReportPeriod period,
        MonthlyReport report,
        UUID requestedEventId,
        ZonedDateTime generatedAt
) {
}
