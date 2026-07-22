package com.cpallas.expenses.reporting.service;

import com.cpallas.expenses.reporting.contract.CategoryReport;
import com.cpallas.expenses.reporting.contract.MonthlyReport;
import com.cpallas.expenses.reporting.contract.MonthlyReportReady;
import com.cpallas.expenses.reporting.contract.ReportPeriod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlyReportMessageFormatterTest {

    private final MonthlyReportMessageFormatter formatter = new MonthlyReportMessageFormatter();

    @Test
    void formatsComparisonLimitCategoriesAndInsights() {
        MonthlyReportReady event = new MonthlyReportReady(
                UUID.randomUUID(), "MonthlyReportReady", 1, UUID.randomUUID(), 42L,
                new ReportPeriod(LocalDate.of(2026, 6, 22), LocalDate.of(2026, 7, 22)),
                new MonthlyReport(
                        new BigDecimal("150000"), new BigDecimal("120000"), new BigDecimal("30000"), new BigDecimal("25"),
                        new BigDecimal("140000"), new BigDecimal("107.1"),
                        List.of(new CategoryReport("Продукты", new BigDecimal("90000"), new BigDecimal("70000"), new BigDecimal("20000"), new BigDecimal("28.6"))),
                        List.of("Лимит превышен на 10000.")
                ),
                UUID.randomUUID(), ZonedDateTime.now()
        );

        String message = formatter.format(event);

        assertThat(message)
                .contains("Месячный отчёт за 22 июня — 21 июля")
                .contains("Всего потрачено: 150000")
                .contains("Предыдущий период: 120000")
                .contains("Разница: +30000 (25%)")
                .contains("Лимит: 140000 (использовано 107.1%)")
                .contains("Продукты: 90000")
                .contains("Лимит превышен на 10000.");
    }
}
