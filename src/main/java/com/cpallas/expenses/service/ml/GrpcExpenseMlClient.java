package com.cpallas.expenses.service.ml;

import com.cpallas.expenses.config.ml.ExpenseMlProperties;
import com.cpallas.expenses.ml.grpc.Category;
import com.cpallas.expenses.ml.grpc.ExpenseClassifierGrpc;
import com.cpallas.expenses.ml.grpc.PredictRequest;
import com.cpallas.expenses.ml.grpc.PredictionResponse;
import com.cpallas.expenses.ml.grpc.TrainingExample;
import com.cpallas.expenses.ml.grpc.UploadTrainingDataRequest;
import com.cpallas.expenses.ml.grpc.UploadTrainingDataResponse;
import com.cpallas.expenses.service.dto.*;
import com.cpallas.expenses.storage.ids.CategoryId;
import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.jpa.CategoryJpa;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrpcExpenseMlClient implements ExpenseMlClient {

    private final ExpenseClassifierGrpc.ExpenseClassifierBlockingStub stub;
    private final ExpenseMlProperties properties;

    @Override
    public ExpenseCategoryPrediction predict(ChatId chatId, QuickExpense expense, List<CategoryJpa> categories) {
        if (!properties.enabled()) {
            return ExpenseCategoryPrediction.reviewOnly(List.of());
        }

        PredictRequest request = PredictRequest.newBuilder()
                .setChatId(chatId.getId())
                .setRawText(expense.rawText())
                .setDescription(expense.description())
                .setAmount(expense.amount().doubleValue())
                .addAllAvailableCategories(categories.stream()
                        .map(this::toGrpcCategory)
                        .toList())
                .build();

        try {
            PredictionResponse response = stub
                    .withDeadlineAfter(properties.deadlineMs(), TimeUnit.MILLISECONDS)
                    .predict(request);
            return toPrediction(response);
        } catch (StatusRuntimeException e) {
            log.warn("Expense ML prediction failed: {}", e.getStatus(), e);
            return ExpenseCategoryPrediction.reviewOnly(List.of());
        }
    }

    @Override
    public UploadTrainingDataResult uploadTrainingData(ChatId chatId,
                                                       boolean replaceChatData,
                                                       boolean trainAfterUpload,
                                                       List<ExpenseTrainingExample> examples) {
        if (!properties.enabled()) {
            return new UploadTrainingDataResult(0, examples.size(), false, "Expense ML integration is disabled.");
        }

        UploadTrainingDataRequest request = UploadTrainingDataRequest.newBuilder()
                .setChatId(chatId.getId())
                .setReplaceChatData(replaceChatData)
                .setTrainAfterUpload(trainAfterUpload)
                .addAllExamples(examples.stream()
                        .map(this::toTrainingExample)
                        .toList())
                .build();

        try {
            UploadTrainingDataResponse response = stub
                    .withDeadlineAfter(properties.deadlineMs(), TimeUnit.MILLISECONDS)
                    .uploadTrainingData(request);
            return new UploadTrainingDataResult(
                    response.getAcceptedCount(),
                    response.getSkippedCount(),
                    response.getTrained(),
                    response.getMessage()
            );
        } catch (StatusRuntimeException e) {
            log.warn("Expense ML training data upload failed: {}", e.getStatus(), e);
            return new UploadTrainingDataResult(0, examples.size(), false, e.getStatus().toString());
        }
    }

    private Category toGrpcCategory(CategoryJpa category) {
        return Category.newBuilder()
                .setId(category.getId().getId().toString())
                .setName(category.getName())
                .build();
    }

    private TrainingExample toTrainingExample(ExpenseTrainingExample example) {
        return TrainingExample.newBuilder()
                .setChatId(example.chatId().getId())
                .setRawText(nullToEmpty(example.rawText()))
                .setDescription(nullToEmpty(example.description()))
                .setAmount(example.amount().doubleValue())
                .setCategoryId(example.categoryId().getId().toString())
                .setCategoryName(example.categoryName())
                .setSource(example.source())
                .build();
    }

    private ExpenseCategoryPrediction toPrediction(PredictionResponse response) {
        List<ExpensePredictionAlternative> alternatives = response.getAlternativesList().stream()
                .map($ -> new ExpensePredictionAlternative(
                        new CategoryId(UUID.fromString($.getCategoryId())),
                        $.getCategoryName(),
                        $.getConfidence()
                ))
                .toList();

        CategoryId categoryId = response.getCategoryId().isBlank()
                ? null
                : new CategoryId(UUID.fromString(response.getCategoryId()));
        return new ExpenseCategoryPrediction(
                categoryId,
                response.getCategoryName(),
                response.getConfidence(),
                response.getNeedsReview(),
                alternatives
        );
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
