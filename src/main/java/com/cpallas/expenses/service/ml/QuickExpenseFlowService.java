package com.cpallas.expenses.service.ml;

import com.cpallas.expenses.enums.FlowType;
import com.cpallas.expenses.enums.Step;
import com.cpallas.expenses.UserSession;
import com.cpallas.expenses.controller.dto.ExpenseActionMenu;
import com.cpallas.expenses.service.ExpenseService;
import com.cpallas.expenses.service.ExpenseMessageFormatter;
import com.cpallas.expenses.service.dto.ExpenseCategoryPrediction;
import com.cpallas.expenses.service.dto.ExpensePredictionAlternative;
import com.cpallas.expenses.service.dto.QuickExpense;
import com.cpallas.expenses.service.util.QuickExpenseParser;
import com.cpallas.expenses.storage.ids.CategoryId;
import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.ids.UserId;
import com.cpallas.expenses.storage.jpa.CategoryJpa;
import com.cpallas.expenses.storage.jpa.ExpenseJpa;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.cpallas.expenses.controller.util.MessageUtil.createBtn;
import static com.cpallas.expenses.controller.util.MessageUtil.createMessage;

@Service
@RequiredArgsConstructor
public class QuickExpenseFlowService {

    static final String QUICK_SHOW_CATEGORIES_CALLBACK = "QUICK_SHOW_CATEGORIES";
    static final String QUICK_CUSTOM_CATEGORY_CALLBACK = "QUICK_CUSTOM_CATEGORY";

    private final TelegramClient telegramClient;
    private final ExpenseService expenseService;
    private final ExpenseMlClient expenseMlClient;
    private final DefaultCategoryClassifier defaultCategoryClassifier;
    private final ExpenseMessageFormatter expenseMessageFormatter;

    public boolean tryStartQuickExpense(Update update, UserSession session) throws TelegramApiException {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return false;
        }

        Optional<QuickExpense> quickExpense = QuickExpenseParser.parse(update.getMessage().getText());
        if (quickExpense.isEmpty()) {
            return false;
        }

        ChatId chatId = new ChatId(getChatIdFromUpdate(update));
        UserId userId = getUserIdFromUpdate(update);
        List<CategoryJpa> categories = expenseService.getOrCreateCategories(chatId, userId);
        ExpenseCategoryPrediction prediction = defaultCategoryClassifier
                .predict(quickExpense.get(), categories)
                .orElseGet(() -> expenseMlClient.predict(chatId, quickExpense.get(), categories));
        if (prediction.acceptedCategoryId().isPresent()) {
            UserSession quickSession = newQuickExpenseSession(quickExpense.get(), prediction.acceptedCategoryId().get());
            ExpenseJpa saved = expenseService.addSpending(userId, chatId, quickSession);
            sendSavedExpense(update, saved);
            return true;
        }

        session.setStep(Step.AWAITING_QUICK_EXPENSE_CATEGORY);
        session.setFlow(FlowType.QUICK_EXPENSE);
        session.setRawText(quickExpense.get().rawText());
        session.setAmount(quickExpense.get().amount());
        session.setDescription(quickExpense.get().description());

        SendMessage message = createMessage(
                "Не уверен в категории для траты \"%s\". Выберите категорию или введите свою.".formatted(quickExpense.get().description()),
                getChatIdFromUpdate(update)
        );
        message.setReplyMarkup(quickCategoryMarkup(reviewAlternatives(prediction), categories));
        telegramClient.execute(message);
        return true;
    }

    public void continueQuickExpense(Update update, UserSession session) throws TelegramApiException {
        if (session.getStep() == Step.AWAITING_QUICK_EXPENSE_CATEGORY) {
            addQuickExpenseCategory(update, session);
            return;
        }
        if (session.getStep() == Step.AWAITING_QUICK_EXPENSE_CATEGORY_NAME) {
            addQuickExpenseCategoryName(update, session);
        }
    }

    private InlineKeyboardMarkup quickCategoryMarkup(List<ExpensePredictionAlternative> alternatives, List<CategoryJpa> categories) {
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        if (alternatives.isEmpty()) {
            addExistingCategoryButtons(keyboard, categories);
        } else {
            alternatives.forEach($ -> keyboard.add(new InlineKeyboardRow(createBtn(
                    "%s (%.0f%%)".formatted($.categoryName(), $.confidence() * 100),
                    $.categoryId().getId().toString()
            ))));
            keyboard.add(new InlineKeyboardRow(createBtn(
                    "Показать существующие категории",
                    QUICK_SHOW_CATEGORIES_CALLBACK
            )));
        }
        keyboard.add(new InlineKeyboardRow(createBtn("Ввести свою категорию", QUICK_CUSTOM_CATEGORY_CALLBACK)));
        return new InlineKeyboardMarkup(keyboard);
    }

    private InlineKeyboardMarkup existingCategoryMarkup(List<CategoryJpa> categories) {
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        addExistingCategoryButtons(keyboard, categories);
        keyboard.add(new InlineKeyboardRow(createBtn(
                "Ввести новую категорию",
                QUICK_CUSTOM_CATEGORY_CALLBACK
        )));
        return new InlineKeyboardMarkup(keyboard);
    }

    private void addExistingCategoryButtons(List<InlineKeyboardRow> keyboard,
                                            List<CategoryJpa> categories) {
        categories.forEach(category -> keyboard.add(new InlineKeyboardRow(createBtn(
                category.getName(),
                category.getId().getId().toString()
        ))));
    }

    private List<ExpensePredictionAlternative> reviewAlternatives(ExpenseCategoryPrediction prediction) {
        if (!prediction.alternatives().isEmpty()) {
            return prediction.alternatives();
        }
        return prediction.acceptedCategoryId()
                .map(categoryId -> List.of(new ExpensePredictionAlternative(
                        categoryId,
                        prediction.categoryName(),
                        prediction.confidence()
                )))
                .orElseGet(List::of);
    }

    private void addQuickExpenseCategory(Update update, UserSession session) throws TelegramApiException {
        if (!update.hasCallbackQuery()) {
            telegramClient.execute(createMessage("Выберите категорию кнопкой или введите свою", getChatIdFromUpdate(update)));
            return;
        }
        if (QUICK_SHOW_CATEGORIES_CALLBACK.equals(update.getCallbackQuery().getData())) {
            List<CategoryJpa> categories = expenseService.getOrCreateCategories(
                    new ChatId(getChatIdFromUpdate(update)),
                    getUserIdFromUpdate(update)
            );
            SendMessage message = createMessage(
                    "Выберите существующую категорию или создайте новую",
                    getChatIdFromUpdate(update)
            );
            message.setReplyMarkup(existingCategoryMarkup(categories));
            telegramClient.execute(message);
            return;
        }
        if (QUICK_CUSTOM_CATEGORY_CALLBACK.equals(update.getCallbackQuery().getData())) {
            session.setStep(Step.AWAITING_QUICK_EXPENSE_CATEGORY_NAME);
            session.setFlow(FlowType.QUICK_EXPENSE);
            telegramClient.execute(createMessage("Введите название категории", getChatIdFromUpdate(update)));
            return;
        }

        session.setCategoryId(new CategoryId(UUID.fromString(update.getCallbackQuery().getData())));
        saveQuickExpense(update, session);
    }

    private void addQuickExpenseCategoryName(Update update, UserSession session) throws TelegramApiException {
        CategoryJpa category = expenseService.createCategory(
                new ChatId(getChatIdFromUpdate(update)),
                getUserIdFromUpdate(update),
                update.getMessage().getText()
        );
        session.setCategoryId(category.getId());
        saveQuickExpense(update, session);
    }

    private void saveQuickExpense(Update update, UserSession session) throws TelegramApiException {
        ExpenseJpa saved = expenseService.addSpending(
                getUserIdFromUpdate(update),
                new ChatId(getChatIdFromUpdate(update)),
                session
        );
        session.setStep(Step.DONE);
        session.setFlow(FlowType.QUICK_EXPENSE);
        sendSavedExpense(update, saved);
    }

    private UserSession newQuickExpenseSession(QuickExpense quickExpense, CategoryId categoryId) {
        UserSession session = new UserSession();
        session.setRawText(quickExpense.rawText());
        session.setAmount(quickExpense.amount());
        session.setDescription(quickExpense.description());
        session.setCategoryId(categoryId);
        return session;
    }

    private Long getChatIdFromUpdate(Update update) {
        if (update.hasMessage()) return update.getMessage().getChatId();
        return update.getCallbackQuery().getMessage().getChatId();
    }

    private UserId getUserIdFromUpdate(Update update) {
        if (update.hasMessage()) return new UserId(update.getMessage().getFrom().getId());
        return new UserId(update.getCallbackQuery().getFrom().getId());
    }

    private void sendSavedExpense(Update update, ExpenseJpa expense) throws TelegramApiException {
        ChatId chatId = new ChatId(getChatIdFromUpdate(update));
        UserId userId = getUserIdFromUpdate(update);
        SendMessage message = createMessage(
                expenseMessageFormatter.saved(expense, expenseService.getStatus(chatId, userId)),
                chatId.getId()
        );
        message.setReplyMarkup(ExpenseActionMenu.afterSave(expense.getId()));
        telegramClient.execute(message);

    }
}
