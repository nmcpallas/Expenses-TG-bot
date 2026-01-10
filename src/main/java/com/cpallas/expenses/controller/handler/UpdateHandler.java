package com.cpallas.expenses.controller.handler;

import com.cpallas.expenses.Step;
import com.cpallas.expenses.UserSession;
import com.cpallas.expenses.controller.dto.CalendarMenu;
import com.cpallas.expenses.controller.dto.CategoryMenu;
import com.cpallas.expenses.controller.dto.GeneralMenu;
import com.cpallas.expenses.controller.dto.Month;
import com.cpallas.expenses.exception.WrongFormat;
import com.cpallas.expenses.service.ExpenseExcelExporter;
import com.cpallas.expenses.storage.ids.CategoryId;
import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.ids.UserId;
import com.cpallas.expenses.service.ExpenseService;
import com.cpallas.expenses.storage.jpa.CategoryJpa;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.cpallas.expenses.controller.util.MessageUtil.createBtn;
import static com.cpallas.expenses.controller.util.MessageUtil.createMessage;

@Slf4j
@Service
public class UpdateHandler {

    private final TelegramClient telegramClient;
    private final ExpenseService expenseService;
    private final Cache<Long, UserSession> sessions = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(1_000)
            .build();
    private static final InlineKeyboardMarkup generalMenuMarkup = GeneralMenu.init();
    private static final InlineKeyboardMarkup calendarMenuMarkup = CalendarMenu.init();

    public UpdateHandler(TelegramClient telegramClient, ExpenseService expenseService) {
        this.telegramClient = telegramClient;
        this.expenseService = expenseService;
    }

    public void handle(Update update) throws TelegramApiException {
        try {
            Optional<UserSession> oSession = Optional.ofNullable(sessions.getIfPresent(getChatIdFromUpdate(update)));
            if (oSession.isPresent()) {
                continueProcess(update, oSession.get());
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                telegramClient.execute(sendGeneralMenu(update));
            } else if (update.hasCallbackQuery()) {
                UserSession newSession = getOrCreateSession(getChatIdFromUpdate(update));
                newSession.setStep(Step.valueOf(update.getCallbackQuery().getData()));
                continueProcess(update, newSession);
            } else {
                telegramClient.execute(createMessage("Я вас не понимаю", getChatIdFromUpdate(update)));
            }
        } catch (Exception e) {
            log.error("Error handling update", e);
            removeSession(getChatIdFromUpdate(update));
            telegramClient.execute(createMessage("Произошла ошибка, попробуйте еще раз", getChatIdFromUpdate(update)));
        }
    }

    private void continueProcess(Update update, UserSession session) throws TelegramApiException {
        releaseButton(update);
        switch (session.getStep()) {
            case SAVING_EXPENSE -> waitingForExpense(update, session);
            case GETTING_CURRENT_STATUS -> getCurrentStatus(update, session);
            case ADDING_MONTH_LIMITATION -> waitingMonthLimitation(update, session);
            case CREATING_EXPENSE_CATEGORY -> waitForCategory(update, session);
            case WAITING_FOR_EXPENSE_CATEGORY_NAME -> addingCategory(update, session);
            case WAITING_FOR_EXPENSE_AMOUNT -> addingExpenseAmount(update, session);
            case WAITING_FOR_EXPENSE_CATEGORY -> addingExpenseCategory(update, session);
            case WAITING_FOR_EXPENSE_DESCRIPTION -> addingExpenseDescription(update, session);
            case WAITING_FOR_MONTH_LIMITATION -> addingMonthLimitation(update, session);
            case DOWNLOAD_EXCEL_FILE -> downloadExcelFile(update, session);
            case WAITING_FOR_DATE_PERIOD -> waitingForDatePeriod(update, session);
            case GENERAL_MENU -> sendingGeneralMenu(update, session);
            default -> telegramClient.execute(sendGeneralMenu(update));
        }
        if (session.getStep().equals(Step.DONE)) {
            removeSession(getChatIdFromUpdate(update));
        }
    }

    private void sendingGeneralMenu(Update update, UserSession session) {
        try {
            releaseButton(update);
            session.setStep(Step.DONE);
            telegramClient.execute(sendGeneralMenu(update));
        } catch (Exception e) {
            log.error("Error during sending general menu", e);
        }
    }

    private void waitingForDatePeriod(Update update, UserSession session) throws TelegramApiException {
        if (!update.hasCallbackQuery()) {
            SendMessage message = createMessage("Выберите месяц в текущем году", getChatIdFromUpdate(update));
            message.setReplyMarkup(calendarMenuMarkup);
            telegramClient.execute(message);
        }
        byte[] excelBytes = ExpenseExcelExporter.exportExpensesToExcel(expenseService.getExpenses(new ChatId(getChatIdFromUpdate(update)),
                Month.valueOf(update.getCallbackQuery().getData())));

        ByteArrayInputStream inputStream = new ByteArrayInputStream(excelBytes);

        InputFile inputFile = new InputFile(inputStream, "expenses.xlsx");

        SendDocument sendDocument = SendDocument.builder()
                .chatId(getChatIdFromUpdate(update))
                .document(inputFile)
                .caption("Вот ваши расходы в формате Excel")
                .build();
        telegramClient.execute(sendDocument);
    }

    private void downloadExcelFile(Update update, UserSession session) throws TelegramApiException {
        session.setStep(Step.WAITING_FOR_DATE_PERIOD);
        SendMessage message = createMessage("Выберите месяц в текущем году", getChatIdFromUpdate(update));
        message.setReplyMarkup(calendarMenuMarkup);
        telegramClient.execute(message);
    }

    private void addingCategory(Update update, UserSession session) throws TelegramApiException {
        try {
            expenseService.createCategory(new ChatId(getChatIdFromUpdate(update)), getUserIdFromUpdate(update), update.getMessage().getText());
            session.setStep(Step.DONE);
            SendMessage message = createMessage("Категория успешно добавлена", getChatIdFromUpdate(update));
            message.setReplyMarkup(backToMenuMarkup());
            telegramClient.execute(message);
        } catch (Exception e) {
            telegramClient.execute(createMessage("Ошибка при добавлении категории, попробуйте еще раз", getChatIdFromUpdate(update)));
            throw e;
        }
    }

    private InlineKeyboardMarkup backToMenuMarkup() {
        return new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(createBtn("Назад к главному меню", Step.GENERAL_MENU.name()))));
    }

    private void waitingMonthLimitation(Update update, UserSession session) throws TelegramApiException {
        session.setStep(Step.WAITING_FOR_MONTH_LIMITATION);
        telegramClient.execute(createMessage("Отправьте сумму ограничения", getChatIdFromUpdate(update)));
    }

    private void getCurrentStatus(Update update, UserSession session) throws TelegramApiException {
        String status = expenseService.getStatus(new ChatId(getChatIdFromUpdate(update)),
                        getUserIdFromUpdate(update))
                .getStatus();
        session.setStep(Step.DONE);
        SendMessage message = createMessage(status, getChatIdFromUpdate(update));
        message.setReplyMarkup(backToMenuMarkup());
        telegramClient.execute(message);
    }

    private void waitForCategory(Update update, UserSession session) throws TelegramApiException {
        session.setStep(Step.WAITING_FOR_EXPENSE_CATEGORY_NAME);
        telegramClient.execute(createMessage("Введите название категории", getChatIdFromUpdate(update)));
    }

    private void addingMonthLimitation(Update update, UserSession session) throws TelegramApiException {
        try {
            expenseService.setOrUpdateLimitation(getUserIdFromUpdate(update),
                    new ChatId(getChatIdFromUpdate(update)),
                    update.getMessage().getText());
            session.setStep(Step.DONE);
            SendMessage message = createMessage("Ограничение успешно установлено", getChatIdFromUpdate(update));
            message.setReplyMarkup(backToMenuMarkup());
            telegramClient.execute(message);
        } catch (WrongFormat e) {
            telegramClient.execute(createMessage("Ошибка в формате суммы ограничения. Используйте, пожалуйста, только цифры. Попробуйте еще раз",
                    getChatIdFromUpdate(update)));
        }
    }

    private void waitingForExpense(Update update, UserSession session) throws TelegramApiException {
        session.setStep(Step.WAITING_FOR_EXPENSE_AMOUNT);
        telegramClient.execute(createMessage("Отправьте потраченную сумму", getChatIdFromUpdate(update)));
    }

    private void addingExpenseAmount(Update update, UserSession session) throws TelegramApiException {
        try {
            session.setAmount(Double.valueOf(update.getMessage().getText().replace(',', '.')));
            session.setStep(Step.WAITING_FOR_EXPENSE_CATEGORY);
            List<CategoryJpa> categories = expenseService.getCategories(new ChatId(getChatIdFromUpdate(update)));
            if (categories.isEmpty()) {
                SendMessage message = createMessage("У вас нет добавленных категорий. Создайте, пожалуйста, категорию и после заново введите трату",
                        getChatIdFromUpdate(update));
                message.setReplyMarkup(CategoryMenu.createCategory());
                telegramClient.execute(message);
                removeSession(getChatIdFromUpdate(update));
                return;
            }
            SendMessage message = createMessage("Выберите категорию траты", getChatIdFromUpdate(update));
            message.setReplyMarkup(CategoryMenu.init(categories));
            telegramClient.execute(message);
        } catch (NumberFormatException e) {
            telegramClient.execute(createMessage("Введено некорректное значение суммы, попробуйте еще раз", getChatIdFromUpdate(update)));
        }
    }

    private void addingExpenseCategory(Update update, UserSession session) throws TelegramApiException {
        if (update.getCallbackQuery().getData().equals(Step.CREATING_EXPENSE_CATEGORY.name())) {
            waitForCategory(update, session);
            return;
        }
        session.setStep(Step.WAITING_FOR_EXPENSE_DESCRIPTION);
        session.setCategoryId(new CategoryId(UUID.fromString(update.getCallbackQuery().getData())));
        telegramClient.execute(createMessage("Введите описание траты", getChatIdFromUpdate(update)));
    }

    private void addingExpenseDescription(Update update, UserSession session) throws TelegramApiException {
        try {
            session.setDescription(update.getMessage().getText());
            expenseService.addSpending(
                    getUserIdFromUpdate(update),
                    new ChatId(getChatIdFromUpdate(update)),
                    session
            );
            session.setStep(Step.DONE);
            SendMessage message = createMessage("Трата успешно сохранена", getChatIdFromUpdate(update));
            message.setReplyMarkup(backToMenuMarkup());
            telegramClient.execute(message);
        } catch (Exception e) {
            telegramClient.execute(createMessage("Ошибка при сохранении траты, попробуйте еще раз", getChatIdFromUpdate(update)));
            throw e;
        }
    }

    private SendMessage sendGeneralMenu(Update update) {
        SendMessage message = createMessage("Выберите дальнейшее действие", getChatIdFromUpdate(update));
        message.setReplyMarkup(generalMenuMarkup);
        return message;
    }

    private void releaseButton(Update update) throws TelegramApiException {
        if (!update.hasCallbackQuery()) return;
        AnswerCallbackQuery answer = new AnswerCallbackQuery(update.getCallbackQuery().getId());
        answer.setText("Processed");
        telegramClient.execute(answer);
    }

    private Long getChatIdFromUpdate(Update update) {
        if (update.hasMessage()) return update.getMessage().getChatId();
        return update.getCallbackQuery().getMessage().getChatId();
    }

    private UserId getUserIdFromUpdate(Update update) {
        if (update.hasMessage()) return new UserId(update.getMessage().getFrom().getId());
        return new UserId(update.getCallbackQuery().getFrom().getId());
    }

    private UserSession getOrCreateSession(Long chatId) {
        return sessions.get(chatId, id -> new UserSession());
    }

    private void removeSession(Long chatId) {
        if (chatId == null) {
            log.warn("Can't remove null chatId from sessions");
            return;
        }
        sessions.invalidate(chatId);
    }
}
