package com.cpallas.expenses.controller.handler;

import com.cpallas.expenses.enums.Step;
import com.cpallas.expenses.UserSession;
import com.cpallas.expenses.controller.dto.GeneralMenu;
import com.cpallas.expenses.service.flow.FlowTypeResolver;
import com.cpallas.expenses.service.flow.FlowDispatcher;
import com.cpallas.expenses.service.ml.QuickExpenseFlowService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
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
    private final QuickExpenseFlowService quickExpenseFlowService;
    private final FlowDispatcher flowDispatcher;
    private final FlowTypeResolver flowTypeResolver;
    private final Cache<Long, UserSession> sessions = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(3))
            .maximumSize(1_000)
            .build();
    private static final InlineKeyboardMarkup generalMenuMarkup = GeneralMenu.init();

    public UpdateHandler(TelegramClient telegramClient,
                         QuickExpenseFlowService quickExpenseFlowService,
                         FlowDispatcher flowDispatcher,
                         FlowTypeResolver flowTypeResolver) {
        this.telegramClient = telegramClient;
        this.quickExpenseFlowService = quickExpenseFlowService;
        this.flowDispatcher = flowDispatcher;
        this.flowTypeResolver = flowTypeResolver;
    }

    public void handle(Update update) throws TelegramApiException {
        Long chatId = getChatIdFromUpdate(update);
        try {
            UserSession session = sessions.getIfPresent(chatId);
            if (session != null) {
                continueProcess(update, session);
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                handleTextMessage(update, chatId);
            } else if (update.hasCallbackQuery()) {
                handleCallback(update, chatId);
            } else {
                telegramClient.execute(createMessage("Я вас не понимаю", chatId));
            }
        } catch (Exception e) {
            log.error("Error handling update", e);
            removeSession(chatId);
            telegramClient.execute(createMessage("Произошла ошибка, попробуйте еще раз", chatId));
        }
    }

    private void handleTextMessage(Update update, Long chatId) throws TelegramApiException {
        UserSession quickSession = getOrCreateSession(chatId);
        if (quickExpenseFlowService.tryStartQuickExpense(update, quickSession)) {
            if (quickSession.getStep() == null || quickSession.getStep().equals(Step.DONE)) {
                removeSession(chatId);
            }
            return;
        }

        removeSession(chatId);
        telegramClient.execute(sendGeneralMenu(update));
    }

    private void handleCallback(Update update, Long chatId) throws TelegramApiException {
        UserSession newSession = getOrCreateSession(chatId);
        newSession.setStep(Step.valueOf(update.getCallbackQuery().getData()));
        newSession.setFlow(flowTypeResolver.resolve(newSession.getStep()));
        continueProcess(update, newSession);
    }

    private void continueProcess(Update update, UserSession session) throws TelegramApiException {
        releaseButton(update);
        flowDispatcher.dispatch(update, session);
        if (session.getStep().equals(Step.DONE)) {
            removeSession(getChatIdFromUpdate(update));
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
