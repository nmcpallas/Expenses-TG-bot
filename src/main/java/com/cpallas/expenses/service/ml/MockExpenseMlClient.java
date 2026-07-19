package com.cpallas.expenses.service.ml;

import com.cpallas.expenses.service.dto.ExpenseCategoryPrediction;
import com.cpallas.expenses.service.dto.ExpensePredictionAlternative;
import com.cpallas.expenses.service.dto.ExpenseTrainingExample;
import com.cpallas.expenses.service.dto.QuickExpense;
import com.cpallas.expenses.service.dto.UploadTrainingDataResult;
import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.jpa.CategoryJpa;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "expense.ml", name = "mock-enabled", havingValue = "true")
public class MockExpenseMlClient implements ExpenseMlClient {

    private static final double ACCEPTED_CONFIDENCE = 0.95;
    private static final double REVIEW_CONFIDENCE = 0.50;

    @Override
    public ExpenseCategoryPrediction predict(ChatId chatId, QuickExpense expense, List<CategoryJpa> categories) {
        if (categories.isEmpty()) {
            return ExpenseCategoryPrediction.reviewOnly(List.of());
        }

        String description = normalize(expense.description());
        return categories.stream()
                .filter(category -> description.contains(normalize(category.getName())))
                .findFirst()
                .map(category -> new ExpenseCategoryPrediction(
                        category.getId(),
                        category.getName(),
                        ACCEPTED_CONFIDENCE,
                        false,
                        alternatives(categories)
                ))
                .orElseGet(() -> ExpenseCategoryPrediction.reviewOnly(alternatives(categories)));
    }

    @Override
    public UploadTrainingDataResult uploadTrainingData(ChatId chatId,
                                                       boolean replaceChatData,
                                                       boolean trainAfterUpload,
                                                       List<ExpenseTrainingExample> examples) {
        log.info(
                "Mock ML training upload: chatId={}, replaceChatData={}, trainAfterUpload={}, examples={}",
                chatId.getId(),
                replaceChatData,
                trainAfterUpload,
                examples.size()
        );
        return new UploadTrainingDataResult(examples.size(), 0, trainAfterUpload, "Mock ML client accepted training data.");
    }

    private List<ExpensePredictionAlternative> alternatives(List<CategoryJpa> categories) {
        return categories.stream()
                .limit(3)
                .map(category -> new ExpensePredictionAlternative(
                        category.getId(),
                        category.getName(),
                        REVIEW_CONFIDENCE
                ))
                .toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
