package com.cpallas.expenses.controller.dto;

import java.math.BigDecimal;
import java.util.Map;

public record SpendingStatus(BigDecimal monthLimit,
        BigDecimal spent,
        Map<String, BigDecimal> spendingByCategories,
        Map<String, BigDecimal> limitsByCategories) {

    public SpendingStatus(BigDecimal monthLimit,
                          BigDecimal spent,
                          Map<String, BigDecimal> spendingByCategories) {
        this(monthLimit, spent, spendingByCategories, Map.of());
    }
}
