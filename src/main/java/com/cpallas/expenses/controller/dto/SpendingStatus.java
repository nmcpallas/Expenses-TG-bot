package com.cpallas.expenses.controller.dto;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

public record SpendingStatus(BigDecimal monthLimit,
        BigDecimal spent,
        Map<String, BigDecimal> spendingByCategories) {

    public String getStatus() {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format(
                Locale.FRANCE,
                "Месячное ограничение: %.1f Потрачено на данный момент: %.1f, Остаток в этом месяце: %.1f\n",
                monthLimit, spent, monthLimit.subtract(spent)));
        spendingByCategories.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(entry ->
                        sb.append(String.format(Locale.FRANCE, "%s: %.1f\n", entry.getKey(), entry.getValue()))
                );

        return sb.toString();
    }
}
