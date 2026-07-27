package com.cpallas.expenses.reporting.contract;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record WeeklyReport(
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal totalAmount,
        BigDecimal previousTotalAmount,
        BigDecimal differenceAmount,
        BigDecimal differencePercent,
        Map<String, BigDecimal> categories,
        int expensesCount,
        String unusualCategory
) {
}
