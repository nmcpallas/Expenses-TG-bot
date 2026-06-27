package com.cpallas.expenses.service.ml;

import com.cpallas.expenses.UserSession;
import com.cpallas.expenses.enums.FlowType;
import com.cpallas.expenses.enums.Step;
import com.cpallas.expenses.service.ExpenseService;
import com.cpallas.expenses.service.dto.ExpenseCategoryPrediction;
import com.cpallas.expenses.service.dto.ExpensePredictionAlternative;
import com.cpallas.expenses.service.dto.QuickExpense;
import com.cpallas.expenses.storage.ids.CategoryId;
import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.ids.UserId;
import com.cpallas.expenses.storage.jpa.CategoryJpa;
import com.cpallas.expenses.storage.jpa.ChatJpa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

import static com.cpallas.expenses.service.flow.FlowTestSupport.CHAT_ID;
import static com.cpallas.expenses.service.flow.FlowTestSupport.USER_ID;
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
class QuickExpenseFlowServiceTest {

    @Mock
    private TelegramClient telegramClient;
    @Mock
    private ExpenseService expenseService;
    @Mock
    private ExpenseMlClient expenseMlClient;

    private QuickExpenseFlowService service;

    @BeforeEach
    void setUp() {
        service = new QuickExpenseFlowService(telegramClient, expenseService, expenseMlClient);
    }

    @Test
    void ignoresTextThatIsNotQuickExpense() throws TelegramApiException {
        UserSession session = new UserSession();

        boolean started = service.tryStartQuickExpense(messageUpdate("просто кофе"), session);

        assertThat(started).isFalse();
        verify(expenseService, never()).getCategories(any(ChatId.class));
        verify(expenseMlClient, never()).predict(any(), any(), any());
    }

    @Test
    void asksToCreateCategoryWhenThereAreNoCategories() throws TelegramApiException {
        UserSession session = new UserSession();
        when(expenseService.getCategories(eq(new ChatId(CHAT_ID)))).thenReturn(Collections.emptyList());

        boolean started = service.tryStartQuickExpense(messageUpdate("250 кофе"), session);

        assertThat(started).isTrue();
        SendMessage message = sendMessages(telegramClient).getFirst();
        assertThat(message.getText()).isEqualTo("У вас нет добавленных категорий. Создайте, пожалуйста, категорию и после заново введите трату");
        assertThat(message.getReplyMarkup()).isNotNull();
        verify(expenseMlClient, never()).predict(any(), any(), any());
    }

    @Test
    void savesExpenseImmediatelyWhenPredictionIsAccepted() throws TelegramApiException {
        CategoryJpa category = category("Кофе");
        when(expenseService.getCategories(eq(new ChatId(CHAT_ID)))).thenReturn(List.of(category));
        when(expenseMlClient.predict(eq(new ChatId(CHAT_ID)), any(QuickExpense.class), eq(List.of(category))))
                .thenReturn(new ExpenseCategoryPrediction(category.getId(), "Кофе", 0.95, false, List.of()));

        boolean started = service.tryStartQuickExpense(messageUpdate("250 кофе"), new UserSession());

        assertThat(started).isTrue();
        ArgumentCaptor<UserSession> sessionCaptor = ArgumentCaptor.forClass(UserSession.class);
        verify(expenseService).addSpending(eq(new UserId(USER_ID)), eq(new ChatId(CHAT_ID)), sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("250"));
        assertThat(sessionCaptor.getValue().getDescription()).isEqualTo("кофе");
        assertThat(sessionCaptor.getValue().getCategoryId().getId()).isEqualTo(category.getId().getId());
        assertThat(sendMessages(telegramClient).getFirst().getText()).isEqualTo("Трата сохранена: 250 · Кофе · кофе");
    }

    @Test
    void startsReviewFlowWhenPredictionNeedsReview() throws TelegramApiException {
        CategoryJpa category = category("Кофе");
        when(expenseService.getCategories(eq(new ChatId(CHAT_ID)))).thenReturn(List.of(category));
        when(expenseMlClient.predict(eq(new ChatId(CHAT_ID)), any(QuickExpense.class), eq(List.of(category))))
                .thenReturn(ExpenseCategoryPrediction.reviewOnly(List.of(
                        new ExpensePredictionAlternative(category.getId(), "Кофе", 0.55)
                )));
        UserSession session = new UserSession();

        boolean started = service.tryStartQuickExpense(messageUpdate("250 кофе"), session);

        assertThat(started).isTrue();
        assertThat(session.getStep()).isEqualTo(Step.AWAITING_QUICK_EXPENSE_CATEGORY);
        assertThat(session.getFlow()).isEqualTo(FlowType.QUICK_EXPENSE);
        assertThat(session.getRawText()).isEqualTo("250 кофе");
        assertThat(session.getAmount()).isEqualByComparingTo(new BigDecimal("250"));
        assertThat(session.getDescription()).isEqualTo("кофе");

        SendMessage message = sendMessages(telegramClient).getFirst();
        assertThat(message.getText()).isEqualTo("Не уверен в категории для траты \"кофе\". Выберите категорию или введите свою.");
        InlineKeyboardMarkup replyMarkup = (InlineKeyboardMarkup) message.getReplyMarkup();
        assertThat(replyMarkup.getKeyboard().getFirst().getFirst().getText()).isEqualTo("Кофе (55%)");
        assertThat(replyMarkup.getKeyboard().get(1).getFirst().getText()).isEqualTo("Ввести свою категорию");
    }

    @Test
    void savesReviewedExpenseWithSelectedCategory() throws TelegramApiException {
        CategoryJpa category = category("Кофе");
        when(expenseService.getCategories(eq(new ChatId(CHAT_ID)))).thenReturn(List.of(category));
        UserSession session = quickReviewSession();

        service.continueQuickExpense(callbackUpdate(category.getId().getId().toString()), session);

        verify(expenseService).addSpending(eq(new UserId(USER_ID)), eq(new ChatId(CHAT_ID)), eq(session));
        assertThat(session.getStep()).isEqualTo(Step.DONE);
        assertThat(session.getFlow()).isEqualTo(FlowType.QUICK_EXPENSE);
        assertThat(session.getCategoryId().getId()).isEqualTo(category.getId().getId());
        assertThat(sendMessages(telegramClient).getFirst().getText()).isEqualTo("Трата сохранена: 250 · Кофе · кофе");
    }

    @Test
    void asksForCustomCategoryName() throws TelegramApiException {
        UserSession session = quickReviewSession();

        service.continueQuickExpense(callbackUpdate("QUICK_CUSTOM_CATEGORY"), session);

        assertThat(session.getStep()).isEqualTo(Step.AWAITING_QUICK_EXPENSE_CATEGORY_NAME);
        assertThat(session.getFlow()).isEqualTo(FlowType.QUICK_EXPENSE);
        assertThat(sendMessages(telegramClient).getFirst().getText()).isEqualTo("Введите название категории");
    }

    @Test
    void createsCustomCategoryAndSavesExpense() throws TelegramApiException {
        CategoryJpa category = category("Кофейни");
        when(expenseService.createCategory(eq(new ChatId(CHAT_ID)), eq(new UserId(USER_ID)), eq("Кофейни")))
                .thenReturn(category);
        UserSession session = quickReviewSession();
        session.setStep(Step.AWAITING_QUICK_EXPENSE_CATEGORY_NAME);

        service.continueQuickExpense(messageUpdate("Кофейни"), session);

        verify(expenseService).addSpending(eq(new UserId(USER_ID)), eq(new ChatId(CHAT_ID)), eq(session));
        assertThat(session.getStep()).isEqualTo(Step.DONE);
        assertThat(session.getCategoryId().getId()).isEqualTo(category.getId().getId());
        assertThat(sendMessages(telegramClient).getFirst().getText()).isEqualTo("Трата сохранена: 250 · Кофейни · кофе");
    }

    private UserSession quickReviewSession() {
        UserSession session = new UserSession();
        session.setStep(Step.AWAITING_QUICK_EXPENSE_CATEGORY);
        session.setFlow(FlowType.QUICK_EXPENSE);
        session.setRawText("250 кофе");
        session.setAmount(new BigDecimal("250"));
        session.setDescription("кофе");
        return session;
    }

    private CategoryJpa category(String name) {
        ChatJpa chat = new ChatJpa();
        chat.setId(new ChatId(CHAT_ID));

        CategoryJpa category = new CategoryJpa();
        category.setId(new CategoryId(UUID.randomUUID()));
        category.setChat(chat);
        category.setName(name);
        return category;
    }
}
