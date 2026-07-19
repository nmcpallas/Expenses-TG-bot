package com.cpallas.expenses.service.dto;

import com.cpallas.expenses.storage.ids.CategoryId;

import java.util.List;
import java.util.Optional;

public record ExpenseCategoryPrediction(
        CategoryId categoryId,
        String categoryName,
        double confidence,
        boolean needsReview,
        List<ExpensePredictionAlternative> alternatives
) {

    public static ExpenseCategoryPrediction reviewOnly(List<ExpensePredictionAlternative> alternatives) {
        return new ExpenseCategoryPrediction(null, "", 0.0, true, alternatives);
    }

    public Optional<CategoryId> acceptedCategoryId() {
        return needsReview ? Optional.empty() : Optional.ofNullable(categoryId);
    }
}
