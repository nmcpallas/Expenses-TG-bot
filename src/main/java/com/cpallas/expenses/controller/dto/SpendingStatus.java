package com.cpallas.expenses.controller.dto;

import lombok.Builder;

import java.util.Locale;

@Builder
public class SpendingStatus {

    private Double spent;
    private Double income;

    public String getStatus() {
        return String.format(
                Locale.FRANCE,
                "Потрачено: %.1f Сумма трат на месяц: %.1f, Остаток в этом месяце: %.1f",
                spent, income, income - spent);
    }
}
