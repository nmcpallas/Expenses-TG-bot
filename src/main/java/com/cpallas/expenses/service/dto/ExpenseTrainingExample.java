package com.cpallas.expenses.service.dto;

import com.cpallas.expenses.storage.ids.CategoryId;
import com.cpallas.expenses.storage.ids.ChatId;

import java.math.BigDecimal;

public record ExpenseTrainingExample(
        ChatId chatId,
        String rawText,
        String description,
        BigDecimal amount,
        CategoryId categoryId,
        String categoryName,
        String source
) {
}
