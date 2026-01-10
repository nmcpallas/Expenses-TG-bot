package com.cpallas.expenses.controller.dto;

import lombok.Builder;

@Builder
public class SpendingStatus {

    private Double spent;
    private Double income;

    public String getStatus() {
        return "Потрачено: %.1f Сумма трат на месяц: %.1f, Остаток в этом месяце: %.1f"
                .formatted(spent, income, income - spent);
    }
}
