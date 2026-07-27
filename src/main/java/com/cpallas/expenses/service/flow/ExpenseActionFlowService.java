package com.cpallas.expenses.service.flow;

import com.cpallas.expenses.UserSession;
import com.cpallas.expenses.controller.dto.ExpenseActionMenu;
import com.cpallas.expenses.enums.FlowType;
import com.cpallas.expenses.enums.Step;
import com.cpallas.expenses.service.ExpenseMessageFormatter;
import com.cpallas.expenses.service.ExpenseService;
import com.cpallas.expenses.storage.ids.CategoryId;
import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.ids.ExpenseId;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static com.cpallas.expenses.controller.dto.ExpenseActionMenu.DELETE_PREFIX;
import static com.cpallas.expenses.controller.dto.ExpenseActionMenu.EDIT_AMOUNT_PREFIX;
import static com.cpallas.expenses.controller.dto.ExpenseActionMenu.EDIT_CATEGORY_PREFIX;
import static com.cpallas.expenses.controller.dto.ExpenseActionMenu.EDIT_DESCRIPTION_PREFIX;
import static com.cpallas.expenses.controller.dto.ExpenseActionMenu.EDIT_PREFIX;
import static com.cpallas.expenses.controller.dto.ExpenseActionMenu.NEW_CATEGORY;
import static com.cpallas.expenses.controller.dto.ExpenseActionMenu.SELECT_CATEGORY_PREFIX;
import static com.cpallas.expenses.controller.util.MessageUtil.createBtn;
import static com.cpallas.expenses.controller.util.MessageUtil.createMessage;

@Service
@RequiredArgsConstructor
public class ExpenseActionFlowService {

    private final TelegramClient telegramClient;
    private final ExpenseService expenseService;
    private final ExpenseMessageFormatter expenseMessageFormatter;

    public boolean tryHandleText(Update update, UserSession session) throws TelegramApiException {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return false;
        }
        String command = update.getMessage().getText().trim().toLowerCase(Locale.ROOT);
        if (isUndoCommand(command)) {
            deleteLast(update, session);
            return true;
        }
        if (isEditLastCommand(command)) {
            Optional<ExpenseJpa> expense = expenseService.getLastExpense(chatId(update), userId(update));
            if (expense.isEmpty()) {
                telegramClient.execute(createMessage("Пока нет трат, которые можно изменить.", chatId(update).getId()));
                session.setStep(Step.DONE);
                return true;
            }
            startEdit(update, session, expense.get().getId());
            return true;
        }
        return false;
    }

    public boolean tryStartFromCallback(Update update, UserSession session) throws TelegramApiException {
        if (!update.hasCallbackQuery()) {
            return false;
        }
        String data = update.getCallbackQuery().getData();
        if (data.startsWith(EDIT_PREFIX)) {
            startEdit(update, session, expenseId(data, EDIT_PREFIX));
            return true;
        }
        if (data.startsWith(DELETE_PREFIX)) {
            delete(update, session, expenseId(data, DELETE_PREFIX));
            return true;
        }
        return false;
    }

    public void handle(Update update, UserSession session) throws TelegramApiException {
        if (update.hasCallbackQuery()) {
            handleCallback(update, session);
            return;
        }
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleTextInput(update, session);
            return;
        }
        telegramClient.execute(createMessage("Отправьте значение текстом.", chatId(update).getId()));
    }

    private void handleCallback(Update update, UserSession session) throws TelegramApiException {
        String data = update.getCallbackQuery().getData();
        if (data.startsWith(EDIT_AMOUNT_PREFIX)) {
            session.setExpenseId(expenseId(data, EDIT_AMOUNT_PREFIX));
            session.setStep(Step.AWAITING_EXPENSE_EDIT_AMOUNT);
            session.setFlow(FlowType.EDIT_EXPENSE);
            telegramClient.execute(createMessage("Введите новую сумму.", chatId(update).getId()));
            return;
        }
        if (data.startsWith(EDIT_DESCRIPTION_PREFIX)) {
            session.setExpenseId(expenseId(data, EDIT_DESCRIPTION_PREFIX));
            session.setStep(Step.AWAITING_EXPENSE_EDIT_DESCRIPTION);
            session.setFlow(FlowType.EDIT_EXPENSE);
            telegramClient.execute(createMessage("Введите новое описание.", chatId(update).getId()));
            return;
        }
        if (data.startsWith(EDIT_CATEGORY_PREFIX)) {
            session.setExpenseId(expenseId(data, EDIT_CATEGORY_PREFIX));
            session.setStep(Step.AWAITING_EXPENSE_EDIT_CATEGORY);
            session.setFlow(FlowType.EDIT_EXPENSE);
            sendCategories(update);
            return;
        }
        if (data.startsWith(SELECT_CATEGORY_PREFIX)) {
            CategoryId categoryId = new CategoryId(UUID.fromString(data.substring(SELECT_CATEGORY_PREFIX.length())));
            ExpenseJpa updated = expenseService.updateExpenseCategory(
                    chatId(update),
                    userId(update),
                    requiredExpenseId(session),
                    categoryId
            );
            finishUpdate(update, session, updated);
            return;
        }
        if (NEW_CATEGORY.equals(data)) {
            session.setStep(Step.AWAITING_EXPENSE_EDIT_CATEGORY_NAME);
            session.setFlow(FlowType.EDIT_EXPENSE);
            telegramClient.execute(createMessage("Введите название новой категории.", chatId(update).getId()));
            return;
        }
        if (data.startsWith(DELETE_PREFIX)) {
            delete(update, session, expenseId(data, DELETE_PREFIX));
            return;
        }
        if (data.startsWith(EDIT_PREFIX)) {
            startEdit(update, session, expenseId(data, EDIT_PREFIX));
            return;
        }
        telegramClient.execute(createMessage("Не удалось распознать действие.", chatId(update).getId()));
    }

    private void handleTextInput(Update update, UserSession session) throws TelegramApiException {
        String text = update.getMessage().getText().trim();
        switch (session.getStep()) {
            case AWAITING_EXPENSE_EDIT_AMOUNT -> {
                try {
                    BigDecimal amount = new BigDecimal(text.replace(" ", "").replace(',', '.'));
                    ExpenseJpa updated = expenseService.updateExpenseAmount(
                            chatId(update),
                            userId(update),
                            requiredExpenseId(session),
                            amount
                    );
                    finishUpdate(update, session, updated);
                } catch (IllegalArgumentException exception) {
                    telegramClient.execute(createMessage(
                            "Не получилось распознать сумму. Например: 35000",
                            chatId(update).getId()
                    ));
                }
            }
            case AWAITING_EXPENSE_EDIT_DESCRIPTION -> {
                ExpenseJpa updated = expenseService.updateExpenseDescription(
                        chatId(update),
                        userId(update),
                        requiredExpenseId(session),
                        text
                );
                finishUpdate(update, session, updated);
            }
            case AWAITING_EXPENSE_EDIT_CATEGORY_NAME -> {
                CategoryJpa category = expenseService.createCategory(chatId(update), userId(update), text);
                ExpenseJpa updated = expenseService.updateExpenseCategory(
                        chatId(update),
                        userId(update),
                        requiredExpenseId(session),
                        category.getId()
                );
                finishUpdate(update, session, updated);
            }
            default -> telegramClient.execute(createMessage(
                    "Выберите, что именно нужно изменить.",
                    chatId(update).getId()
            ));
        }
    }

    private void startEdit(Update update, UserSession session, ExpenseId expenseId) throws TelegramApiException {
        Optional<ExpenseJpa> expense = expenseService.getExpense(chatId(update), userId(update), expenseId);
        if (expense.isEmpty()) {
            telegramClient.execute(createMessage("Эта трата уже удалена или не найдена.", chatId(update).getId()));
            session.setStep(Step.DONE);
            return;
        }
        session.setExpenseId(expenseId);
        session.setStep(Step.AWAITING_EXPENSE_EDIT_ACTION);
        session.setFlow(FlowType.EDIT_EXPENSE);
        SendMessage message = createMessage(
                "Что изменить в трате?\n%s · %s · %s".formatted(
                        expense.get().getAmount().stripTrailingZeros().toPlainString(),
                        expense.get().getCategory().getName(),
                        expense.get().getDescription()
                ),
                chatId(update).getId()
        );
        message.setReplyMarkup(ExpenseActionMenu.edit(expenseId));
        telegramClient.execute(message);
    }

    private void deleteLast(Update update, UserSession session) throws TelegramApiException {
        Optional<ExpenseJpa> deleted = expenseService.deleteLastExpense(chatId(update), userId(update));
        if (deleted.isEmpty()) {
            telegramClient.execute(createMessage("Пока нет трат, которые можно отменить.", chatId(update).getId()));
        } else {
            telegramClient.execute(createMessage(
                    expenseMessageFormatter.deleted(deleted.get()),
                    chatId(update).getId()
            ));
        }
        session.setStep(Step.DONE);
        session.setFlow(FlowType.EDIT_EXPENSE);
    }

    private void delete(Update update, UserSession session, ExpenseId expenseId) throws TelegramApiException {
        Optional<ExpenseJpa> deleted = expenseService.deleteExpense(chatId(update), userId(update), expenseId);
        if (deleted.isEmpty()) {
            telegramClient.execute(createMessage("Эта трата уже удалена.", chatId(update).getId()));
        } else {
            telegramClient.execute(createMessage(
                    expenseMessageFormatter.deleted(deleted.get()),
                    chatId(update).getId()
            ));
        }
        session.setStep(Step.DONE);
        session.setFlow(FlowType.EDIT_EXPENSE);
    }

    private void sendCategories(Update update) throws TelegramApiException {
        List<CategoryJpa> categories = expenseService.getOrCreateCategories(chatId(update), userId(update));
        List<InlineKeyboardRow> rows = new ArrayList<>();
        categories.forEach(category -> rows.add(new InlineKeyboardRow(createBtn(
                category.getName(),
                SELECT_CATEGORY_PREFIX + category.getId().getId()
        ))));
        rows.add(new InlineKeyboardRow(createBtn("Создать категорию", NEW_CATEGORY)));
        SendMessage message = createMessage("Выберите категорию.", chatId(update).getId());
        message.setReplyMarkup(new InlineKeyboardMarkup(rows));
        telegramClient.execute(message);
    }

    private void finishUpdate(Update update, UserSession session, ExpenseJpa updated) throws TelegramApiException {
        session.setStep(Step.DONE);
        session.setFlow(FlowType.EDIT_EXPENSE);
        SendMessage message = createMessage(expenseMessageFormatter.updated(updated), chatId(update).getId());
        message.setReplyMarkup(ExpenseActionMenu.afterSave(updated.getId()));
        telegramClient.execute(message);
    }

    private ExpenseId requiredExpenseId(UserSession session) {
        if (session.getExpenseId() == null) {
            throw new IllegalStateException("Expense edit target is missing.");
        }
        return session.getExpenseId();
    }

    private ExpenseId expenseId(String data, String prefix) {
        return new ExpenseId(UUID.fromString(data.substring(prefix.length())));
    }

    private boolean isUndoCommand(String command) {
        return command.equals("/undo")
                || command.equals("отмени последнюю")
                || command.equals("отменить последнюю")
                || command.equals("удали последнюю")
                || command.equals("удали последнюю трату");
    }

    private boolean isEditLastCommand(String command) {
        return command.equals("измени последнюю")
                || command.equals("изменить последнюю")
                || command.equals("измени последнюю трату");
    }

    private ChatId chatId(Update update) {
        if (update.hasMessage()) {
            return new ChatId(update.getMessage().getChatId());
        }
        return new ChatId(update.getCallbackQuery().getMessage().getChatId());
    }

    private UserId userId(Update update) {
        if (update.hasMessage()) {
            return new UserId(update.getMessage().getFrom().getId());
        }
        return new UserId(update.getCallbackQuery().getFrom().getId());
    }
}
