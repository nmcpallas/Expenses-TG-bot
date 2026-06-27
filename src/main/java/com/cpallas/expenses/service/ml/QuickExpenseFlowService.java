package com.cpallas.expenses.service.ml;

import com.cpallas.expenses.enums.Step;
import com.cpallas.expenses.UserSession;
import com.cpallas.expenses.controller.dto.CategoryMenu;
import com.cpallas.expenses.service.ExpenseService;
import com.cpallas.expenses.service.dto.ExpenseCategoryPrediction;
import com.cpallas.expenses.service.dto.ExpensePredictionAlternative;
import com.cpallas.expenses.service.dto.QuickExpense;
import com.cpallas.expenses.service.util.QuickExpenseParser;
import com.cpallas.expenses.storage.ids.CategoryId;
import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.ids.UserId;
import com.cpallas.expenses.storage.jpa.CategoryJpa;
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

    private static final String QUICK_CUSTOM_CATEGORY_CALLBACK = "QUICK_CUSTOM_CATEGORY";

    private final TelegramClient telegramClient;
    private final ExpenseService expenseService;
    private final ExpenseMlClient expenseMlClient;

    public boolean tryStartQuickExpense(Update update, UserSession session) throws TelegramApiException {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return false;
        }

        Optional<QuickExpense> quickExpense = QuickExpenseParser.parse(update.getMessage().getText());
        if (quickExpense.isEmpty()) {
            return false;
        }

        ChatId chatId = new ChatId(getChatIdFromUpdate(update));
        List<CategoryJpa> categories = expenseService.getCategories(chatId);
        if (categories.isEmpty()) {
            SendMessage message = createMessage("У вас нет добавленных категорий. Создайте, пожалуйста, категорию и после заново введите трату",
                    getChatIdFromUpdate(update));
            message.setReplyMarkup(CategoryMenu.createCategory());
            telegramClient.execute(message);
            return true;
        }

        ExpenseCategoryPrediction prediction = expenseMlClient.predict(chatId, quickExpense.get(), categories);
        if (prediction.acceptedCategoryId().isPresent()) {
            UserSession quickSession = newQuickExpenseSession(quickExpense.get(), prediction.acceptedCategoryId().get());
            expenseService.addSpending(getUserIdFromUpdate(update), chatId, quickSession);
            telegramClient.execute(createMessage(
                    "Трата сохранена: %s · %s · %s".formatted(
                            quickExpense.get().amount(),
                            prediction.categoryName(),
                            quickExpense.get().description()
                    ),
                    getChatIdFromUpdate(update)
            ));
            return true;
        }

        session.setStep(Step.WAITING_FOR_QUICK_EXPENSE_CATEGORY);
        session.setRawText(quickExpense.get().rawText());
        session.setAmount(quickExpense.get().amount());
        session.setDescription(quickExpense.get().description());

        SendMessage message = createMessage(
                "Не уверен в категории для траты \"%s\". Выберите категорию или введите свою.".formatted(quickExpense.get().description()),
                getChatIdFromUpdate(update)
        );
        message.setReplyMarkup(quickCategoryMarkup(prediction.alternatives(), categories));
        telegramClient.execute(message);
        return true;
    }

    public void continueQuickExpense(Update update, UserSession session) throws TelegramApiException {
        if (session.getStep() == Step.WAITING_FOR_QUICK_EXPENSE_CATEGORY) {
            addQuickExpenseCategory(update, session);
            return;
        }
        if (session.getStep() == Step.WAITING_FOR_QUICK_EXPENSE_CATEGORY_NAME) {
            addQuickExpenseCategoryName(update, session);
        }
    }

    private InlineKeyboardMarkup quickCategoryMarkup(List<ExpensePredictionAlternative> alternatives, List<CategoryJpa> categories) {
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        if (alternatives.isEmpty()) {
            categories.forEach($ -> keyboard.add(new InlineKeyboardRow(createBtn($.getName(), $.getId().getId().toString()))));
        } else {
            alternatives.forEach($ -> keyboard.add(new InlineKeyboardRow(createBtn(
                    "%s (%.0f%%)".formatted($.categoryName(), $.confidence() * 100),
                    $.categoryId().getId().toString()
            ))));
        }
        keyboard.add(new InlineKeyboardRow(createBtn("Ввести свою категорию", QUICK_CUSTOM_CATEGORY_CALLBACK)));
        return new InlineKeyboardMarkup(keyboard);
    }

    private void addQuickExpenseCategory(Update update, UserSession session) throws TelegramApiException {
        if (!update.hasCallbackQuery()) {
            telegramClient.execute(createMessage("Выберите категорию кнопкой или введите свою", getChatIdFromUpdate(update)));
            return;
        }
        if (QUICK_CUSTOM_CATEGORY_CALLBACK.equals(update.getCallbackQuery().getData())) {
            session.setStep(Step.WAITING_FOR_QUICK_EXPENSE_CATEGORY_NAME);
            telegramClient.execute(createMessage("Введите название категории", getChatIdFromUpdate(update)));
            return;
        }

        session.setCategoryId(new CategoryId(UUID.fromString(update.getCallbackQuery().getData())));
        saveQuickExpense(update, session, findCategoryName(new ChatId(getChatIdFromUpdate(update)), session.getCategoryId()));
    }

    private void addQuickExpenseCategoryName(Update update, UserSession session) throws TelegramApiException {
        CategoryJpa category = expenseService.createCategory(
                new ChatId(getChatIdFromUpdate(update)),
                getUserIdFromUpdate(update),
                update.getMessage().getText()
        );
        session.setCategoryId(category.getId());
        saveQuickExpense(update, session, category.getName());
    }

    private void saveQuickExpense(Update update, UserSession session, String categoryName) throws TelegramApiException {
        expenseService.addSpending(
                getUserIdFromUpdate(update),
                new ChatId(getChatIdFromUpdate(update)),
                session
        );
        session.setStep(Step.DONE);
        telegramClient.execute(createMessage(
                "Трата сохранена: %s · %s · %s".formatted(session.getAmount(), categoryName, session.getDescription()),
                getChatIdFromUpdate(update)
        ));
    }

    private UserSession newQuickExpenseSession(QuickExpense quickExpense, CategoryId categoryId) {
        UserSession session = new UserSession();
        session.setRawText(quickExpense.rawText());
        session.setAmount(quickExpense.amount());
        session.setDescription(quickExpense.description());
        session.setCategoryId(categoryId);
        return session;
    }

    private String findCategoryName(ChatId chatId, CategoryId categoryId) {
        return expenseService.getCategories(chatId).stream()
                .filter($ -> $.getId().getId().equals(categoryId.getId()))
                .map(CategoryJpa::getName)
                .findFirst()
                .orElse("выбранная категория");
    }

    private Long getChatIdFromUpdate(Update update) {
        if (update.hasMessage()) return update.getMessage().getChatId();
        return update.getCallbackQuery().getMessage().getChatId();
    }

    private UserId getUserIdFromUpdate(Update update) {
        if (update.hasMessage()) return new UserId(update.getMessage().getFrom().getId());
        return new UserId(update.getCallbackQuery().getFrom().getId());
    }
}
