package com.cpallas.expenses.service.dto;

import com.cpallas.expenses.storage.ids.CategoryId;

public record ExpensePredictionAlternative(
        CategoryId categoryId,
        String categoryName,
        double confidence
) {
}
