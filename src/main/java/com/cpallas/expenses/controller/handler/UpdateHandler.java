package com.cpallas.expenses.controller.handler;

import com.cpallas.expenses.Step;
import com.cpallas.expenses.UserSession;
import com.cpallas.expenses.controller.dto.GeneralMenu;
import com.cpallas.expenses.exception.WrongFormat;
import com.cpallas.expenses.ids.ChatId;
import com.cpallas.expenses.ids.UserId;
import com.cpallas.expenses.service.ExpenseService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.Duration;

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
    private static final InlineKeyboardMarkup inlineKeyboardMarkup = GeneralMenu.init();

    public UpdateHandler(TelegramClient telegramClient, ExpenseService expenseService) {
        this.telegramClient = telegramClient;
        this.expenseService = expenseService;
    }

    public void handle(Update update) throws TelegramApiException {
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleMessage(update);
        } else if (update.hasCallbackQuery()) {
            handleCallback(update);
        } else {
            SendMessage message = createMessage("Я вас не понимаю", update.getMessage().getChatId());
            telegramClient.execute(message);
        }
    }

    @SneakyThrows
    private void handleCallback(Update update) {
        String callBackData = update.getCallbackQuery().getData();
        SendMessage message = null;
        if (callBackData.equals(Step.SAVING_EXPENSE.name())) {
            getOrCreateSession(update.getCallbackQuery().getMessage().getChatId())
                    .setStep(Step.SAVING_EXPENSE);
            message = createMessage("Отправьте значение и описание в форме 'сумма:описание", update.getCallbackQuery().getMessage().getChatId());
        }
        if (callBackData.equals(Step.GETTING_STATISTICS.name())) {
            String status = expenseService.getStatus(new ChatId(update.getCallbackQuery().getMessage().getChatId()),
                            new UserId(update.getCallbackQuery().getFrom().getId()))
                    .getStatus();
            message = createMessage(status, update.getCallbackQuery().getMessage().getChatId());
            removeSession(update.getCallbackQuery().getMessage().getChatId());
        }
        if (callBackData.equals(Step.ADDING_MONTH_LIMITATION.name())) {
            getOrCreateSession(update.getCallbackQuery().getMessage().getChatId())
                    .setStep(Step.ADDING_MONTH_LIMITATION);
            message = createMessage("Отправьте сумму ограничения", update.getCallbackQuery().getMessage().getChatId());
        }
        telegramClient.execute(message);
    }

    private void handleMessage(Update update) throws TelegramApiException {
        UserSession session = getOrCreateSession(update.getMessage().getChatId());
        SendMessage message;
        try {
            message = switch (session.getStep()) {
                case SAVING_EXPENSE -> getSavingExpenseMessage(update);
                case ADDING_MONTH_LIMITATION -> getAddingMonthLimitationMessage(update);
                default -> getMessage(update);
            };
        } catch (WrongFormat e) {
            message = createMessage(e.getMessage(), update.getMessage().getChatId());
            log.error(e.getMessage(), e);
        }
        telegramClient.execute(message);
    }

    private SendMessage getAddingMonthLimitationMessage(Update update) throws WrongFormat {
        expenseService.setOrUpdateLimitation(new UserId(update.getMessage().getFrom().getId()),
                new ChatId(update.getMessage().getChatId()),
                update.getMessage().getText());
        SendMessage message = createMessage("Ограничение успешно установлено", update.getMessage().getChatId());
        removeSession(update.getMessage().getChatId());
        return message;
    }

    private SendMessage getSavingExpenseMessage(Update update) throws WrongFormat {
        expenseService.addSpending(new UserId(update.getMessage().getFrom().getId()), new ChatId(update.getMessage().getChatId()), update.getMessage().getText());
        SendMessage message = createMessage("Трата успешно сохранена", update.getMessage().getChatId());
        removeSession(update.getMessage().getChatId());
        return message;
    }

    @NotNull
    private SendMessage getMessage(Update update) {
        SendMessage message = createMessage("Выберите дальнейшее действие", update.getMessage().getChatId());
        message.setReplyMarkup(inlineKeyboardMarkup);
        return message;
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
