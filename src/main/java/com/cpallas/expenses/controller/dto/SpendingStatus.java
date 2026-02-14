package com.cpallas.expenses.controller.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.Locale;

@Builder
public class SpendingStatus {

    private BigDecimal spent;
    private BigDecimal income;

    public String getStatus() {
        return String.format(
                Locale.FRANCE,
                "Потрачено: %.1f Сумма трат на месяц: %.1f, Остаток в этом месяце: %.1f",
                spent, income, income.subtract(spent));
    }
}
