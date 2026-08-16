package com.cpallas.expenses;

import com.cpallas.expenses.controller.handler.UpdateHandler;
import com.cpallas.expenses.enums.FlowType;
import com.cpallas.expenses.enums.Step;
import com.cpallas.expenses.miniapp.MiniAppLaunchContextService;
import com.cpallas.expenses.service.flow.ExpenseActionFlowService;
import com.cpallas.expenses.service.ml.QuickExpenseFlowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateHandlerTest {

    @Mock
    private TelegramClient telegramClient;
    @Mock
    private QuickExpenseFlowService quickExpenseFlowService;
    @Mock
    private ExpenseActionFlowService expenseActionFlowService;

    @Test
    void startShowsShortDescriptionAndMiniAppButtonNextToHelp() throws Exception {
        handler().handle(textUpdate(1L, 1L, "/start"));

        SendMessage response = sendMessages().getFirst();
        assertThat(response.getText())
                .contains("Просто напишите трату")
                .contains("кофе 35000")
                .contains("пришлю отчёты");
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) response.getReplyMarkup();
        assertThat(markup.getKeyboard()).singleElement().satisfies(row -> {
            assertThat(row).hasSize(2);
            assertThat(row.getFirst().getText()).isEqualTo("Открыть бюджет");
            assertThat(row.getFirst().getWebApp().getUrl())
                    .isEqualTo("https://budget.example.test");
            assertThat(row.getLast().getText()).isEqualTo("Help");
            assertThat(row.getLast().getCallbackData()).isEqualTo("HELP");
        });
    }

    @Test
    void helpCommandShowsTheSameShortDescription() throws Exception {
        handler().handle(textUpdate(1L, 1L, "/help"));

        SendMessage response = sendMessages().getFirst();
        assertThat(response.getText())
                .contains("Просто напишите трату")
                .contains("О необычной трате предупрежу");
    }

    @Test
    void helpButtonShowsDescription() throws Exception {
        handler().handle(callbackUpdate(1L, 1L, "HELP"));

        SendMessage response = sendMessages().getFirst();
        assertThat(response.getText())
                .contains("Просто напишите трату")
                .contains("пришлю отчёты");
    }

    @Test
    void startInGroupShowsPrivateChatLinkInsteadOfUnsupportedWebAppButton() throws Exception {
        handler().handle(textUpdate(-100123L, 1L, "/start", "group"));

        SendMessage response = sendMessages().getFirst();
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) response.getReplyMarkup();
        assertThat(markup.getKeyboard().getFirst().getFirst()).satisfies(button -> {
            assertThat(button.getText()).isEqualTo("Открыть общий бюджет");
            assertThat(button.getWebApp()).isNull();
            assertThat(button.getUrl())
                    .startsWith("https://t.me/expenses_statistic_bot?startapp=v1_");
        });
    }

    @Test
    void appCommandInGroupShowsSharedBudgetButton() throws Exception {
        handler().handle(textUpdate(-100123L, 42L, "/app", "supergroup"));

        SendMessage response = sendMessages().getFirst();
        assertThat(response.getText()).isEqualTo("Общий бюджет этого чата.");
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) response.getReplyMarkup();
        assertThat(markup.getKeyboard().getFirst().getFirst()).satisfies(button -> {
            assertThat(button.getText()).isEqualTo("Открыть общий бюджет");
            assertThat(button.getWebApp()).isNull();
            assertThat(button.getUrl())
                    .startsWith("https://t.me/expenses_statistic_bot?startapp=v1_");
        });
    }

    @Test
    void oldMenuCallbackReturnsTextHintWithoutNewMenu() throws Exception {
        Update update = callbackUpdate(1L, 1L, "SHOW_CURRENT_STATUS");

        handler().handle(update);

        SendMessage response = sendMessages().getFirst();
        assertThat(response.getText())
                .contains("Эта кнопка больше не используется")
                .contains("такси 45000");
        assertThat(response.getReplyMarkup()).isNull();
    }

    @Test
    void keepsExpenseSessionSeparateForUsersInSharedChat() throws Exception {
        AtomicReference<UserSession> firstUserSession = new AtomicReference<>();
        when(quickExpenseFlowService.tryStartQuickExpense(any(), any()))
                .thenAnswer(invocation -> {
                    Update update = invocation.getArgument(0);
                    UserSession session = invocation.getArgument(1);
                    if (update.getMessage().getFrom().getId().equals(1L)) {
                        session.setFlow(FlowType.QUICK_EXPENSE);
                        session.setStep(Step.AWAITING_QUICK_EXPENSE_CATEGORY);
                        firstUserSession.set(session);
                        return true;
                    }
                    return false;
                });
        UpdateHandler handler = handler();

        handler.handle(textUpdate(99L, 1L, "цветы 250"));
        handler.handle(textUpdate(99L, 2L, "100"));

        assertThat(sendMessages().getLast().getText())
                .contains("Не получилось распознать действие");

        Update firstUserCallback = callbackUpdate(
                99L,
                1L,
                UUID.randomUUID().toString()
        );
        handler.handle(firstUserCallback);

        verify(quickExpenseFlowService).continueQuickExpense(
                same(firstUserCallback),
                same(firstUserSession.get())
        );
    }

    private UpdateHandler handler() {
        return new UpdateHandler(
                telegramClient,
                quickExpenseFlowService,
                expenseActionFlowService,
                "https://budget.example.test",
                "@expenses_statistic_bot",
                new MiniAppLaunchContextService("123456:test-token")
        );
    }

    private Update textUpdate(Long chatId, Long userId, String text) {
        return textUpdate(chatId, userId, text, "private");
    }

    private Update textUpdate(Long chatId, Long userId, String text, String chatType) {
        Update update = new Update();
        Message message = new Message();
        message.setText(text);
        message.setFrom(new User(userId, "test", false));
        message.setChat(new Chat(chatId, chatType));
        update.setMessage(message);
        return update;
    }

    private Update callbackUpdate(Long chatId, Long userId, String data) {
        Update update = new Update();
        CallbackQuery callback = new CallbackQuery();
        callback.setId(UUID.randomUUID().toString());
        callback.setData(data);
        callback.setFrom(new User(userId, "test", false));
        Message message = new Message();
        message.setChat(new Chat(chatId, "private"));
        callback.setMessage(message);
        update.setCallbackQuery(callback);
        return update;
    }

    private List<SendMessage> sendMessages() throws Exception {
        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());
        return captor.getAllValues();
    }
}
