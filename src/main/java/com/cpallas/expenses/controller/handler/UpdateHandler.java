package com.cpallas.expenses.controller.handler;

import com.cpallas.expenses.enums.Step;
import com.cpallas.expenses.UserSession;
import com.cpallas.expenses.enums.FlowType;
import com.cpallas.expenses.miniapp.MiniAppLaunchContextService;
import com.cpallas.expenses.service.flow.ExpenseActionFlowService;
import com.cpallas.expenses.service.ml.QuickExpenseFlowService;
import com.cpallas.expenses.storage.ids.ChatId;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.cpallas.expenses.controller.util.MessageUtil.createBtn;
import static com.cpallas.expenses.controller.util.MessageUtil.createMessage;
import static com.cpallas.expenses.controller.util.MessageUtil.createUrlBtn;
import static com.cpallas.expenses.controller.util.MessageUtil.createWebAppBtn;

@Slf4j
@Service
public class UpdateHandler {

    static final String HELP_CALLBACK = "HELP";
    private static final String HELP_TEXT = """
            👋 Просто напишите трату: «кофе 35000».

            Я подберу категорию, покажу статус и пришлю отчёты. О необычной трате предупрежу.
            """.trim();

    private final TelegramClient telegramClient;
    private final QuickExpenseFlowService quickExpenseFlowService;
    private final ExpenseActionFlowService expenseActionFlowService;
    private final String miniAppUrl;
    private final String botUsername;
    private final MiniAppLaunchContextService launchContextService;
    private final Cache<SessionKey, UserSession> sessions = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(3))
            .maximumSize(1_000)
            .build();

    public UpdateHandler(TelegramClient telegramClient,
                         QuickExpenseFlowService quickExpenseFlowService,
                         ExpenseActionFlowService expenseActionFlowService,
                         @Value("${expense.mini-app.url:}") String miniAppUrl,
                         @Value("${telegram.bot.username:}") String botUsername,
                         MiniAppLaunchContextService launchContextService) {
        this.telegramClient = telegramClient;
        this.quickExpenseFlowService = quickExpenseFlowService;
        this.expenseActionFlowService = expenseActionFlowService;
        this.miniAppUrl = miniAppUrl == null ? "" : miniAppUrl.trim();
        this.botUsername = normalizeBotUsername(botUsername);
        this.launchContextService = launchContextService;
    }

    public void handle(Update update) throws TelegramApiException {
        Long chatId = getChatIdFromUpdate(update);
        SessionKey sessionKey = new SessionKey(chatId, getUserIdFromUpdate(update));
        try {
            if (isApp(update)) {
                removeSession(sessionKey);
                sendApp(chatId, isPrivateChat(update));
                return;
            }
            if (isStart(update) || isHelp(update)) {
                removeSession(sessionKey);
                releaseButton(update);
                sendHelp(
                        chatId,
                        isStart(update),
                        isPrivateChat(update)
                );
                return;
            }
            UserSession session = sessions.getIfPresent(sessionKey);
            if (session != null) {
                continueProcess(update, session);
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                handleTextMessage(update, sessionKey);
            } else if (update.hasCallbackQuery()) {
                handleCallback(update, sessionKey);
            } else {
                telegramClient.execute(createMessage("Я вас не понимаю", chatId));
            }
        } catch (Exception e) {
            log.error("Error handling update", e);
            removeSession(sessionKey);
            telegramClient.execute(createMessage(
                    """
                    Произошла ошибка, попробуйте ещё раз.

                    Добавьте трату сообщением, например: «такси 45000».
                    """.trim(),
                    chatId
            ));
        }
    }

    private void handleTextMessage(Update update, SessionKey sessionKey) throws TelegramApiException {
        Long chatId = sessionKey.chatId();
        UserSession quickSession = getOrCreateSession(sessionKey);
        if (expenseActionFlowService.tryHandleText(update, quickSession)) {
            if (quickSession.getStep() == null || quickSession.getStep() == Step.DONE) {
                removeSession(sessionKey);
            }
            return;
        }
        if (quickExpenseFlowService.tryStartQuickExpense(update, quickSession)) {
            if (quickSession.getStep() == null || quickSession.getStep().equals(Step.DONE)) {
                removeSession(sessionKey);
            }
            return;
        }

        removeSession(sessionKey);
        telegramClient.execute(createMessage(
                """
                Не получилось распознать действие.

                Добавьте трату сообщением, например: «такси 45000».
                Также можно написать «отмени последнюю» или «измени последнюю».
                """.trim(),
                chatId
        ));
    }

    private void handleCallback(Update update, SessionKey sessionKey) throws TelegramApiException {
        UserSession newSession = new UserSession();
        if (expenseActionFlowService.tryStartFromCallback(update, newSession)) {
            sessions.put(sessionKey, newSession);
            releaseButton(update);
            if (newSession.getStep() == Step.DONE) {
                removeSession(sessionKey);
            }
            return;
        }
        releaseButton(update);
        removeSession(sessionKey);
        telegramClient.execute(createMessage(
                """
                Эта кнопка больше не используется.

                Просто отправьте трату сообщением, например: «такси 45000».
                """.trim(),
                sessionKey.chatId()
        ));
    }

    private void continueProcess(Update update, UserSession session) throws TelegramApiException {
        releaseButton(update);
        if (session.getFlow() == FlowType.QUICK_EXPENSE) {
            quickExpenseFlowService.continueQuickExpense(update, session);
        } else if (session.getFlow() == FlowType.EDIT_EXPENSE) {
            expenseActionFlowService.handle(update, session);
        } else {
            throw new IllegalStateException("Unsupported session flow: " + session.getFlow());
        }
        if (session.getStep() == Step.DONE) {
            removeSession(new SessionKey(
                    getChatIdFromUpdate(update),
                    getUserIdFromUpdate(update)
            ));
        }
    }

    private void releaseButton(Update update) throws TelegramApiException {
        if (!update.hasCallbackQuery()) return;
        AnswerCallbackQuery answer = new AnswerCallbackQuery(update.getCallbackQuery().getId());
        answer.setText("Processed");
        telegramClient.execute(answer);
    }

    private boolean isStart(Update update) {
        return hasCommand(update, "/start");
    }

    private boolean isHelp(Update update) {
        return hasCommand(update, "/help")
                || update.hasCallbackQuery()
                && HELP_CALLBACK.equals(update.getCallbackQuery().getData());
    }

    private boolean isApp(Update update) {
        return hasCommand(update, "/app");
    }

    private boolean hasCommand(Update update, String command) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return false;
        }
        String text = update.getMessage().getText().trim();
        return command.equalsIgnoreCase(text)
                || text.regionMatches(true, 0, command + "@", 0, command.length() + 1)
                || text.regionMatches(true, 0, command + " ", 0, command.length() + 1);
    }

    private void sendHelp(Long chatId,
                          boolean showHelpButton,
                          boolean privateChat) throws TelegramApiException {
        SendMessage message = createMessage(HELP_TEXT, chatId);
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        appButton(chatId, privateChat).ifPresent(buttons::add);
        if (showHelpButton) {
            buttons.add(createBtn("Help", HELP_CALLBACK));
        }
        if (!buttons.isEmpty()) {
            message.setReplyMarkup(new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(buttons))));
        }
        telegramClient.execute(message);
    }

    private void sendApp(Long chatId, boolean privateChat)
            throws TelegramApiException {
        SendMessage message = createMessage(
                privateChat ? "Ваш личный бюджет." : "Общий бюджет этого чата.",
                chatId
        );
        appButton(chatId, privateChat).ifPresent(button ->
                message.setReplyMarkup(new InlineKeyboardMarkup(
                        List.of(new InlineKeyboardRow(button))
                ))
        );
        telegramClient.execute(message);
    }

    private java.util.Optional<InlineKeyboardButton> appButton(
            Long chatId,
            boolean privateChat
    ) {
        if (miniAppUrl.isBlank()) {
            return java.util.Optional.empty();
        }
        if (privateChat) {
            return java.util.Optional.of(createWebAppBtn("Открыть бюджет", miniAppUrl));
        }
        if (botUsername.isBlank()) {
            return java.util.Optional.empty();
        }
        String startParam = launchContextService.createGroupStartParam(
                new ChatId(chatId)
        );
        return java.util.Optional.of(createUrlBtn(
                "Открыть общий бюджет",
                "https://t.me/" + botUsername + "?startapp=" + startParam
        ));
    }

    private boolean isPrivateChat(Update update) {
        if (update.hasMessage() && update.getMessage().getChat() != null) {
            return Boolean.TRUE.equals(update.getMessage().getChat().isUserChat());
        }
        return update.hasCallbackQuery()
                && update.getCallbackQuery().getMessage() != null
                && update.getCallbackQuery().getMessage().getChat() != null
                && Boolean.TRUE.equals(update.getCallbackQuery().getMessage().getChat().isUserChat());
    }

    private String normalizeBotUsername(String username) {
        if (username == null) {
            return "";
        }
        return username.trim().replaceFirst("^@", "");
    }

    private Long getChatIdFromUpdate(Update update) {
        if (update.hasMessage()) return update.getMessage().getChatId();
        return update.getCallbackQuery().getMessage().getChatId();
    }

    private Long getUserIdFromUpdate(Update update) {
        if (update.hasMessage() && update.getMessage().getFrom() != null) {
            return update.getMessage().getFrom().getId();
        }
        if (update.hasCallbackQuery() && update.getCallbackQuery().getFrom() != null) {
            return update.getCallbackQuery().getFrom().getId();
        }
        return 0L;
    }

    private UserSession getOrCreateSession(SessionKey sessionKey) {
        return sessions.get(sessionKey, id -> new UserSession());
    }

    private void removeSession(SessionKey sessionKey) {
        if (sessionKey == null || sessionKey.chatId() == null) {
            log.warn("Can't remove session without chatId");
            return;
        }
        sessions.invalidate(sessionKey);
    }

    private record SessionKey(Long chatId, Long userId) {
    }
}
