package com.cpallas.expenses.controller.dto;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

public record SpendingStatus(BigDecimal spent,
        BigDecimal income,
        Map<String, BigDecimal> spedingByCategories) {

    public String getStatus() {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format(
                Locale.FRANCE,
                "Потрачено: %.1f Сумма трат на месяц: %.1f, Остаток в этом месяце: %.1f\n",
                spent, income, income.subtract(spent)));
        spedingByCategories.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(entry ->
                        sb.append(String.format(Locale.FRANCE, "%s: %.1f\n", entry.getKey(), entry.getValue()))
                );

        return sb.toString();
    }
}
