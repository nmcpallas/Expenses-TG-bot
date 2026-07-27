package com.cpallas.expenses.reporting.contract;

import java.time.ZonedDateTime;
import java.util.UUID;

public record MonthlyReportRequested(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Long chatId,
        ReportPeriod period,
        ReportPeriod comparisonPeriod,
        String timezone,
        ZonedDateTime requestedAt
) {
    public static MonthlyReportRequested of(UUID eventId,
                                            Long chatId,
                                            ReportPeriod period,
                                            ReportPeriod comparisonPeriod,
                                            String timezone) {
        return new MonthlyReportRequested(
                eventId,
                "MonthlyReportRequested",
                2,
                chatId,
                period,
                comparisonPeriod,
                timezone,
                ZonedDateTime.now()
        );
    }
}
