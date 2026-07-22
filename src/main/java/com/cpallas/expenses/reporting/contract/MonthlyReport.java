package com.cpallas.expenses.reporting.contract;

import java.math.BigDecimal;
import java.util.List;

public record MonthlyReport(
        BigDecimal totalAmount,
        BigDecimal previousTotalAmount,
        BigDecimal differenceAmount,
        BigDecimal differencePercent,
        BigDecimal monthLimit,
        BigDecimal limitUsagePercent,
        List<CategoryReport> categories,
        List<String> insights
) {
}
