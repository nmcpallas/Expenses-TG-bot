package com.cpallas.expenses.controller.dto;

import lombok.Builder;

@Builder
public class SpendingStatus {

    private Double spent;
    private Double income;

    public String getStatus() {
        return "Потрачено: %s Сумма трат на месяц: %s, Остаток в этом месяце: %s"
                .formatted(spent, income, income - spent);
    }
}
