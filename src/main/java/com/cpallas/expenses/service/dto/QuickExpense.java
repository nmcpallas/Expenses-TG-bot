package com.cpallas.expenses.service.dto;

import java.math.BigDecimal;

public record QuickExpense(
        String rawText,
        BigDecimal amount,
        String description
) {
}
