package com.cpallas.expenses.service.flow;

import com.cpallas.expenses.UserSession;
import com.cpallas.expenses.controller.dto.CategoryMenu;
import com.cpallas.expenses.enums.FlowType;
import com.cpallas.expenses.enums.Step;
import com.cpallas.expenses.service.ExpenseService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.cpallas.expenses.controller.util.MessageUtil.createBtn;
import static com.cpallas.expenses.controller.util.MessageUtil.createMessage;

@Service
@RequiredArgsConstructor
public class AddExpenseFlowService {

    private final TelegramClient telegramClient;
    private final ExpenseService expenseService;

    public void handle(Update update, UserSession session) throws TelegramApiException {
        switch (session.getStep()) {
            case START_ADD_EXPENSE -> waitForExpenseAmount(update, session);
            case AWAITING_EXPENSE_AMOUNT -> addExpenseAmount(update, session);
            case AWAITING_EXPENSE_CATEGORY -> addExpenseCategory(update, session);
            case AWAITING_EXPENSE_DESCRIPTION -> addExpenseDescription(update, session);
            default -> throw new IllegalStateException("Unsupported add expense step: " + session.getStep());
        }
    }

    private void waitForExpenseAmount(Update update, UserSession session) throws TelegramApiException {
        session.setStep(Step.AWAITING_EXPENSE_AMOUNT);
        session.setFlow(FlowType.ADD_EXPENSE);
        telegramClient.execute(createMessage("Отправьте потраченную сумму", getChatIdFromUpdate(update)));
    }

    private void addExpenseAmount(Update update, UserSession session) throws TelegramApiException {
        try {
            session.setAmount(new BigDecimal(update.getMessage().getText().replace(',', '.')));
            session.setStep(Step.AWAITING_EXPENSE_CATEGORY);
            session.setFlow(FlowType.ADD_EXPENSE);
            List<CategoryJpa> categories = expenseService.getCategories(new ChatId(getChatIdFromUpdate(update)));
            if (categories.isEmpty()) {
                SendMessage message = createMessage("У вас нет добавленных категорий. Создайте, пожалуйста, категорию и после заново введите трату",
                        getChatIdFromUpdate(update));
                message.setReplyMarkup(CategoryMenu.createCategory());
                telegramClient.execute(message);
                session.setStep(Step.DONE);
                return;
            }
            SendMessage message = createMessage("Выберите категорию траты", getChatIdFromUpdate(update));
            message.setReplyMarkup(CategoryMenu.init(categories));
            telegramClient.execute(message);
        } catch (NumberFormatException e) {
            telegramClient.execute(createMessage("Введено некорректное значение суммы, попробуйте еще раз", getChatIdFromUpdate(update)));
        }
    }

    private void addExpenseCategory(Update update, UserSession session) throws TelegramApiException {
        if (isStartAddCategory(update.getCallbackQuery().getData())) {
            waitForCategoryName(update, session);
            return;
        }
        session.setStep(Step.AWAITING_EXPENSE_DESCRIPTION);
        session.setFlow(FlowType.ADD_EXPENSE);
        session.setCategoryId(new CategoryId(UUID.fromString(update.getCallbackQuery().getData())));
        telegramClient.execute(createMessage("Введите описание траты", getChatIdFromUpdate(update)));
    }

    private boolean isStartAddCategory(String callbackData) {
        return Step.START_ADD_CATEGORY.name().equals(callbackData);
    }

    private void addExpenseDescription(Update update, UserSession session) throws TelegramApiException {
        try {
            session.setDescription(update.getMessage().getText());
            expenseService.addSpending(
                    getUserIdFromUpdate(update),
                    new ChatId(getChatIdFromUpdate(update)),
                    session
            );
            session.setStep(Step.DONE);
            session.setFlow(FlowType.ADD_EXPENSE);
            SendMessage message = createMessage("Трата успешно сохранена", getChatIdFromUpdate(update));
            message.setReplyMarkup(backToMenuMarkup());
            telegramClient.execute(message);
        } catch (Exception e) {
            telegramClient.execute(createMessage("Ошибка при сохранении траты, попробуйте еще раз", getChatIdFromUpdate(update)));
            throw e;
        }
    }

    private InlineKeyboardMarkup backToMenuMarkup() {
        return new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(createBtn("Назад к главному меню", Step.SHOW_GENERAL_MENU.name()))));
    }

    private void waitForCategoryName(Update update, UserSession session) throws TelegramApiException {
        session.setStep(Step.AWAITING_CATEGORY_NAME);
        session.setFlow(FlowType.ADD_CATEGORY);
        telegramClient.execute(createMessage("Введите название категории", getChatIdFromUpdate(update)));
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
