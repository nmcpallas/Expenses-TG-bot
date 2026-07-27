package com.cpallas.expenses.reporting.contract;

import java.time.ZonedDateTime;
import java.util.UUID;

public record WeeklyReportRequested(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Long chatId,
        ReportPeriod period,
        ReportPeriod comparisonPeriod,
        String timezone,
        ZonedDateTime requestedAt
) {
    public static WeeklyReportRequested of(UUID eventId,
                                           Long chatId,
                                           ReportPeriod period,
                                           ReportPeriod comparisonPeriod,
                                           String timezone) {
        return new WeeklyReportRequested(
                eventId,
                "WeeklyReportRequested",
                1,
                chatId,
                period,
                comparisonPeriod,
                timezone,
                ZonedDateTime.now()
        );
    }
}
