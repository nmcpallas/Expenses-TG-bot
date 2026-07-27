package com.cpallas.expenses.reporting.contract;

import java.time.ZonedDateTime;
import java.util.UUID;

public record WeeklyReportReady(
        UUID eventId,
        String eventType,
        int schemaVersion,
        UUID reportId,
        Long chatId,
        ReportPeriod period,
        WeeklyReport report,
        ReportArtifact artifact,
        UUID requestedEventId,
        ZonedDateTime generatedAt
) {
}
