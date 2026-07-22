package com.cpallas.expenses.reporting.contract;

import java.math.BigDecimal;

public record CategoryReport(
        String name,
        BigDecimal amount,
        BigDecimal previousAmount,
        BigDecimal differenceAmount,
        BigDecimal differencePercent
) {
}
