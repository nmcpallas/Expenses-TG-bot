package com.cpallas.expenses.service.flow;

import com.cpallas.expenses.UserSession;
import com.cpallas.expenses.enums.FlowType;
import com.cpallas.expenses.enums.Step;
import com.cpallas.expenses.service.ExpenseService;
import com.cpallas.expenses.storage.ids.CategoryId;
import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.ids.UserId;
import com.cpallas.expenses.storage.jpa.CategoryJpa;
import com.cpallas.expenses.storage.jpa.ChatJpa;
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
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.cpallas.expenses.service.flow.FlowTestSupport.callbackUpdate;
import static com.cpallas.expenses.service.flow.FlowTestSupport.messageUpdate;
import static com.cpallas.expenses.service.flow.FlowTestSupport.sendMessages;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddExpenseFlowServiceTest {

    @Mock
    private TelegramClient telegramClient;
    @Mock
    private ExpenseService expenseService;

    private AddExpenseFlowService service;

    @BeforeEach
    void setUp() {
        service = new AddExpenseFlowService(telegramClient, expenseService);
    }

    @Test
    void asksForExpenseAmountOnStart() throws TelegramApiException {
        UserSession session = new UserSession();
        session.setStep(Step.START_ADD_EXPENSE);

        service.handle(callbackUpdate(Step.START_ADD_EXPENSE.name()), session);

        assertThat(session.getStep()).isEqualTo(Step.AWAITING_EXPENSE_AMOUNT);
        assertThat(session.getFlow()).isEqualTo(FlowType.ADD_EXPENSE);
        assertThat(sendMessages(telegramClient).getFirst().getText()).isEqualTo("Отправьте потраченную сумму");
    }

    @Test
    void storesAmountAndAsksForCategory() throws TelegramApiException {
        CategoryJpa category = category("Кофе");
        UserSession session = new UserSession();
        session.setStep(Step.AWAITING_EXPENSE_AMOUNT);
        when(expenseService.getCategories(eq(new ChatId(FlowTestSupport.CHAT_ID)))).thenReturn(List.of(category));

        service.handle(messageUpdate("250,50"), session);

        assertThat(session.getAmount()).isEqualByComparingTo(new BigDecimal("250.50"));
        assertThat(session.getStep()).isEqualTo(Step.AWAITING_EXPENSE_CATEGORY);
        assertThat(session.getFlow()).isEqualTo(FlowType.ADD_EXPENSE);

        SendMessage message = sendMessages(telegramClient).getFirst();
        assertThat(message.getText()).isEqualTo("Выберите категорию траты");
        InlineKeyboardMarkup replyMarkup = (InlineKeyboardMarkup) message.getReplyMarkup();
        assertThat(replyMarkup.getKeyboard().getFirst().getFirst().getText()).isEqualTo("Кофе");
    }

    @Test
    void completesFlowWhenThereAreNoCategories() throws TelegramApiException {
        UserSession session = new UserSession();
        session.setStep(Step.AWAITING_EXPENSE_AMOUNT);
        when(expenseService.getCategories(eq(new ChatId(FlowTestSupport.CHAT_ID)))).thenReturn(Collections.emptyList());

        service.handle(messageUpdate("250"), session);

        assertThat(session.getStep()).isEqualTo(Step.DONE);
        SendMessage message = sendMessages(telegramClient).getFirst();
        assertThat(message.getText()).isEqualTo("У вас нет добавленных категорий. Создайте, пожалуйста, категорию и после заново введите трату");
        assertThat(message.getReplyMarkup()).isNotNull();
    }

    @Test
    void keepsAmountStepOnInvalidAmount() throws TelegramApiException {
        UserSession session = new UserSession();
        session.setStep(Step.AWAITING_EXPENSE_AMOUNT);

        service.handle(messageUpdate("abc"), session);

        assertThat(session.getStep()).isEqualTo(Step.AWAITING_EXPENSE_AMOUNT);
        assertThat(sendMessages(telegramClient).getFirst().getText())
                .isEqualTo("Введено некорректное значение суммы, попробуйте еще раз");
        verify(expenseService, never()).getCategories(any(ChatId.class));
    }

    @Test
    void storesCategoryAndAsksForDescription() throws TelegramApiException {
        CategoryId categoryId = new CategoryId(UUID.randomUUID());
        UserSession session = new UserSession();
        session.setStep(Step.AWAITING_EXPENSE_CATEGORY);

        service.handle(callbackUpdate(categoryId.getId().toString()), session);

        assertThat(session.getStep()).isEqualTo(Step.AWAITING_EXPENSE_DESCRIPTION);
        assertThat(session.getFlow()).isEqualTo(FlowType.ADD_EXPENSE);
        assertThat(session.getCategoryId().getId()).isEqualTo(categoryId.getId());
        assertThat(sendMessages(telegramClient).getFirst().getText()).isEqualTo("Введите описание траты");
    }

    @Test
    void switchesToAddCategoryFlowFromCategorySelection() throws TelegramApiException {
        UserSession session = new UserSession();
        session.setStep(Step.AWAITING_EXPENSE_CATEGORY);

        service.handle(callbackUpdate(Step.START_ADD_CATEGORY.name()), session);

        assertThat(session.getStep()).isEqualTo(Step.AWAITING_CATEGORY_NAME);
        assertThat(session.getFlow()).isEqualTo(FlowType.ADD_CATEGORY);
        assertThat(sendMessages(telegramClient).getFirst().getText()).isEqualTo("Введите название категории");
    }

    @Test
    void savesExpenseDescriptionAndCompletesFlow() throws TelegramApiException {
        UserSession session = new UserSession();
        session.setStep(Step.AWAITING_EXPENSE_DESCRIPTION);
        session.setAmount(new BigDecimal("250"));
        session.setCategoryId(new CategoryId(UUID.randomUUID()));

        service.handle(messageUpdate("капучино"), session);

        verify(expenseService).addSpending(eq(new UserId(FlowTestSupport.USER_ID)), eq(new ChatId(FlowTestSupport.CHAT_ID)), eq(session));
        assertThat(session.getDescription()).isEqualTo("капучино");
        assertThat(session.getStep()).isEqualTo(Step.DONE);
        assertThat(session.getFlow()).isEqualTo(FlowType.ADD_EXPENSE);

        SendMessage message = sendMessages(telegramClient).getFirst();
        assertThat(message.getText()).isEqualTo("Трата успешно сохранена");
        assertThat(message.getReplyMarkup()).isNotNull();
    }

    private CategoryJpa category(String name) {
        ChatJpa chat = new ChatJpa();
        chat.setId(new ChatId(FlowTestSupport.CHAT_ID));

        CategoryJpa category = new CategoryJpa();
        category.setId(new CategoryId(UUID.randomUUID()));
        category.setChat(chat);
        category.setName(name);
        return category;
    }
}
