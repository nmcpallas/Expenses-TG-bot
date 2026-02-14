package com.cpallas.expenses.service.dto;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record ExpenseExportRow(
        BigDecimal amount,
        String categoryName,
        String description,
        ZonedDateTime createdAt
) {}
