package com.cpallas.expenses.service.flow;

import com.cpallas.expenses.UserSession;
import com.cpallas.expenses.enums.FlowType;
import com.cpallas.expenses.enums.Step;
import com.cpallas.expenses.service.ExpenseMessageFormatter;
import com.cpallas.expenses.service.ExpenseService;
import com.cpallas.expenses.storage.ids.CategoryId;
import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.ids.ExpenseId;
import com.cpallas.expenses.storage.ids.UserId;
import com.cpallas.expenses.storage.jpa.CategoryJpa;
import com.cpallas.expenses.storage.jpa.ChatJpa;
import com.cpallas.expenses.storage.jpa.ExpenseJpa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static com.cpallas.expenses.service.flow.FlowTestSupport.CHAT_ID;
import static com.cpallas.expenses.service.flow.FlowTestSupport.USER_ID;
import static com.cpallas.expenses.service.flow.FlowTestSupport.callbackUpdate;
import static com.cpallas.expenses.service.flow.FlowTestSupport.messageUpdate;
import static com.cpallas.expenses.service.flow.FlowTestSupport.sendMessages;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseActionFlowServiceTest {

    @Mock
    private TelegramClient telegramClient;
    @Mock
    private ExpenseService expenseService;

    private ExpenseActionFlowService service;

    @BeforeEach
    void setUp() {
        service = new ExpenseActionFlowService(
                telegramClient,
                expenseService,
                new ExpenseMessageFormatter()
        );
    }

    @Test
    void undoLastExpenseByTextCommand() throws TelegramApiException {
        ExpenseJpa expense = expense();
        when(expenseService.deleteLastExpense(new ChatId(CHAT_ID), new UserId(USER_ID)))
                .thenReturn(Optional.of(expense));
        UserSession session = new UserSession();

        boolean handled = service.tryHandleText(messageUpdate("отмени последнюю"), session);

        assertThat(handled).isTrue();
        assertThat(session.getStep()).isEqualTo(Step.DONE);
        assertThat(sendMessages(telegramClient).getFirst().getText())
                .isEqualTo("Отменено: 250 · Кафе · кофе");
    }

    @Test
    void opensEditMenuForSavedExpense() throws TelegramApiException {
        ExpenseJpa expense = expense();
        when(expenseService.getExpense(
                new ChatId(CHAT_ID),
                new UserId(USER_ID),
                expense.getId()
        )).thenReturn(Optional.of(expense));
        UserSession session = new UserSession();

        boolean handled = service.tryStartFromCallback(
                callbackUpdate("ee:" + expense.getId().getId()),
                session
        );

        assertThat(handled).isTrue();
        assertThat(session.getStep()).isEqualTo(Step.AWAITING_EXPENSE_EDIT_ACTION);
        assertThat(session.getFlow()).isEqualTo(FlowType.EDIT_EXPENSE);
        SendMessage message = sendMessages(telegramClient).getFirst();
        assertThat(message.getText()).contains("Что изменить в трате?");
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) message.getReplyMarkup();
        assertThat(markup.getKeyboard()).hasSize(4);
    }

    @Test
    void updatesAmountInActiveEditFlow() throws TelegramApiException {
        ExpenseJpa expense = expense();
        expense.setAmount(new BigDecimal("400"));
        when(expenseService.updateExpenseAmount(
                new ChatId(CHAT_ID),
                new UserId(USER_ID),
                expense.getId(),
                new BigDecimal("400")
        )).thenReturn(expense);
        UserSession session = new UserSession();
        session.setFlow(FlowType.EDIT_EXPENSE);
        session.setStep(Step.AWAITING_EXPENSE_EDIT_AMOUNT);
        session.setExpenseId(expense.getId());

        service.handle(messageUpdate("400"), session);

        verify(expenseService).updateExpenseAmount(
                new ChatId(CHAT_ID),
                new UserId(USER_ID),
                expense.getId(),
                new BigDecimal("400")
        );
        assertThat(session.getStep()).isEqualTo(Step.DONE);
        assertThat(sendMessages(telegramClient).getFirst().getText())
                .isEqualTo("✓ Трата изменена: 400 · Кафе · кофе");
    }

    private ExpenseJpa expense() {
        ChatJpa chat = new ChatJpa();
        chat.setId(new ChatId(CHAT_ID));
        CategoryJpa category = new CategoryJpa();
        category.setId(new CategoryId(UUID.randomUUID()));
        category.setChat(chat);
        category.setName("Кафе");
        ExpenseJpa expense = new ExpenseJpa();
        expense.setId(new ExpenseId(UUID.randomUUID()));
        expense.setChat(chat);
        expense.setCategory(category);
        expense.setAmount(new BigDecimal("250"));
        expense.setDescription("кофе");
        return expense;
    }
}
