package com.cpallas.expenses.service.ml;

import com.cpallas.expenses.config.ml.ExpenseMlProperties;
import com.cpallas.expenses.service.dto.ExpenseTrainingExample;
import com.cpallas.expenses.service.dto.UploadTrainingDataResult;
import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.jpa.ChatJpa;
import com.cpallas.expenses.storage.jpa.ExpenseJpa;
import com.cpallas.expenses.storage.repo.ChatRepo;
import com.cpallas.expenses.storage.repo.ExpenseRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseTrainingDataJobService {

    private static final String SOURCE = "DAILY_JOB";

    private final ChatRepo chatRepo;
    private final ExpenseRepo expenseRepo;
    private final ExpenseMlClient expenseMlClient;
    private final ExpenseMlProperties properties;

    @Scheduled(cron = "0 0 2 * * *")
    public void scheduledUploadTrainingData() {
        uploadTrainingData();
    }

    public TrainingDataUploadSummary uploadTrainingData() {
        if (!properties.enabled()) {
            log.debug("Skipping expense ML training data upload: ML integration is disabled.");
            return TrainingDataUploadSummary.disabled();
        }

        List<ChatJpa> chats = chatRepo.findAll();
        log.info("Starting expense ML training data upload for {} chats.", chats.size());

        List<ChatTrainingDataUploadResult> results = new ArrayList<>();
        for (ChatJpa chat : chats) {
            results.add(uploadChatTrainingData(chat.getId()));
        }

        TrainingDataUploadSummary summary = TrainingDataUploadSummary.from(chats.size(), results);
        log.info(
                "Expense ML training data upload finished: chats={}, uploadedChats={}, skippedChats={}, examples={}, accepted={}, skippedExamples={}, trainedChats={}",
                summary.totalChats(),
                summary.uploadedChats(),
                summary.skippedChats(),
                summary.examplesCount(),
                summary.acceptedCount(),
                summary.skippedExamplesCount(),
                summary.trainedChats()
        );
        return summary;
    }

    private ChatTrainingDataUploadResult uploadChatTrainingData(ChatId chatId) {
        List<ExpenseTrainingExample> examples = expenseRepo.findTrainingExamplesByChatId(chatId).stream()
                .map(this::toTrainingExample)
                .toList();

        if (examples.isEmpty()) {
            log.debug("Skipping expense ML training data upload for chat {}: no categorized expenses.", chatId.getId());
            return ChatTrainingDataUploadResult.skipped(chatId, "No categorized expenses.");
        }

        UploadTrainingDataResult result = expenseMlClient.uploadTrainingData(
                chatId,
                true,
                true,
                examples
        );

        log.info(
                "Expense ML training data uploaded for chat {}: examples={}, accepted={}, skipped={}, trained={}, message={}",
                chatId.getId(),
                examples.size(),
                result.acceptedCount(),
                result.skippedCount(),
                result.trained(),
                result.message()
        );
        return ChatTrainingDataUploadResult.uploaded(chatId, examples.size(), result);
    }

    private ExpenseTrainingExample toTrainingExample(ExpenseJpa expense) {
        return new ExpenseTrainingExample(
                expense.getChat().getId(),
                buildRawText(expense),
                expense.getDescription(),
                expense.getAmount(),
                expense.getCategory().getId(),
                expense.getCategory().getName(),
                SOURCE
        );
    }

    private String buildRawText(ExpenseJpa expense) {
        BigDecimal amount = expense.getAmount();
        String description = expense.getDescription();
        if (amount == null) {
            return nullToEmpty(description);
        }
        return "%s %s".formatted(amount.stripTrailingZeros().toPlainString(), nullToEmpty(description)).trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record TrainingDataUploadSummary(
            boolean mlEnabled,
            int totalChats,
            int uploadedChats,
            int skippedChats,
            int examplesCount,
            int acceptedCount,
            int skippedExamplesCount,
            int trainedChats,
            List<ChatTrainingDataUploadResult> chats
    ) {

        private static TrainingDataUploadSummary disabled() {
            return new TrainingDataUploadSummary(false, 0, 0, 0, 0, 0, 0, 0, List.of());
        }

        private static TrainingDataUploadSummary from(int totalChats, List<ChatTrainingDataUploadResult> chats) {
            int uploadedChats = (int) chats.stream().filter(ChatTrainingDataUploadResult::uploaded).count();
            int skippedChats = totalChats - uploadedChats;
            int examplesCount = chats.stream().mapToInt(ChatTrainingDataUploadResult::examplesCount).sum();
            int acceptedCount = chats.stream().mapToInt(ChatTrainingDataUploadResult::acceptedCount).sum();
            int skippedExamplesCount = chats.stream().mapToInt(ChatTrainingDataUploadResult::skippedExamplesCount).sum();
            int trainedChats = (int) chats.stream().filter(ChatTrainingDataUploadResult::trained).count();
            return new TrainingDataUploadSummary(
                    true,
                    totalChats,
                    uploadedChats,
                    skippedChats,
                    examplesCount,
                    acceptedCount,
                    skippedExamplesCount,
                    trainedChats,
                    chats
            );
        }
    }

    public record ChatTrainingDataUploadResult(
            long chatId,
            boolean uploaded,
            int examplesCount,
            int acceptedCount,
            int skippedExamplesCount,
            boolean trained,
            String message
    ) {

        private static ChatTrainingDataUploadResult skipped(ChatId chatId, String message) {
            return new ChatTrainingDataUploadResult(chatId.getId(), false, 0, 0, 0, false, message);
        }

        private static ChatTrainingDataUploadResult uploaded(
                ChatId chatId,
                int examplesCount,
                UploadTrainingDataResult result
        ) {
            return new ChatTrainingDataUploadResult(
                    chatId.getId(),
                    true,
                    examplesCount,
                    result.acceptedCount(),
                    result.skippedCount(),
                    result.trained(),
                    result.message()
            );
        }
    }
}
