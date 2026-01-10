package com.cpallas.expenses.service.dto;

import java.time.ZonedDateTime;

public record ExpenseExportRow(
        Double amount,
        String categoryName,
        String description,
        ZonedDateTime createdAt
) {}
