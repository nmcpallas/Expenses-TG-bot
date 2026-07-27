package com.cpallas.expenses.service.ml;

import com.cpallas.expenses.UserSession;
import com.cpallas.expenses.controller.dto.SpendingStatus;
import com.cpallas.expenses.enums.FlowType;
import com.cpallas.expenses.enums.Step;
import com.cpallas.expenses.service.ExpenseMessageFormatter;
import com.cpallas.expenses.service.ExpenseService;
import com.cpallas.expenses.service.dto.ExpenseCategoryPrediction;
import com.cpallas.expenses.service.dto.ExpensePredictionAlternative;
import com.cpallas.expenses.service.dto.QuickExpense;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
        service = new QuickExpenseFlowService(
                telegramClient,
                expenseService,
                expenseMlClient,
                new DefaultCategoryClassifier(),
                new ExpenseMessageFormatter()
        );
    }

    @Test
    void ignoresTextThatIsNotExpense() throws TelegramApiException {
        boolean started = service.tryStartQuickExpense(messageUpdate("просто кофе"), new UserSession());

        assertThat(started).isFalse();
        verify(expenseService, never()).getOrCreateCategories(any(), any());
    }

    @Test
    void savesFirstExpenseUsingDefaultCategoryWithoutHistoryThreshold() throws TelegramApiException {
        CategoryJpa cafe = category("Кафе");
        ExpenseJpa saved = expense(cafe, "250", "кофе");
        when(expenseService.getOrCreateCategories(new ChatId(CHAT_ID), new UserId(USER_ID)))
                .thenReturn(List.of(cafe, category("Другое")));
        when(expenseService.addSpending(eq(new UserId(USER_ID)), eq(new ChatId(CHAT_ID)), any()))
                .thenReturn(saved);
        when(expenseService.getStatus(new ChatId(CHAT_ID), new UserId(USER_ID)))
                .thenReturn(status("250", "Кафе"));

        boolean started = service.tryStartQuickExpense(messageUpdate("кофе 250"), new UserSession());

        assertThat(started).isTrue();
        verify(expenseMlClient, never()).predict(any(), any(), any());
        ArgumentCaptor<UserSession> session = ArgumentCaptor.forClass(UserSession.class);
        verify(expenseService).addSpending(eq(new UserId(USER_ID)), eq(new ChatId(CHAT_ID)), session.capture());
        assertThat(session.getValue().getCategoryId()).isEqualTo(cafe.getId());

        SendMessage message = sendMessages(telegramClient).getFirst();
        assertThat(message.getText()).contains("✓ 250 · Кафе · кофе");
        assertThat(message.getText()).contains("В категории «Кафе» за текущий период: 250");
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) message.getReplyMarkup();
        assertThat(markup.getKeyboard().getFirst().getFirst().getText()).isEqualTo("Изменить");
        assertThat(markup.getKeyboard().getFirst().get(1).getText()).isEqualTo("Отменить");
    }

    @Test
    void offersExistingCategoriesWhenPredictionNeedsReview() throws TelegramApiException {
        CategoryJpa products = category("Продукты");
        when(expenseService.getOrCreateCategories(new ChatId(CHAT_ID), new UserId(USER_ID)))
                .thenReturn(List.of(products));
        when(expenseMlClient.predict(eq(new ChatId(CHAT_ID)), any(QuickExpense.class), eq(List.of(products))))
                .thenReturn(ExpenseCategoryPrediction.reviewOnly(List.of(
                        new ExpensePredictionAlternative(products.getId(), "Продукты", 0.55)
                )));
        UserSession session = new UserSession();

        boolean started = service.tryStartQuickExpense(messageUpdate("цветы 250"), session);

        assertThat(started).isTrue();
        assertThat(session.getStep()).isEqualTo(Step.AWAITING_QUICK_EXPENSE_CATEGORY);
        assertThat(session.getFlow()).isEqualTo(FlowType.QUICK_EXPENSE);
        SendMessage message = sendMessages(telegramClient).getFirst();
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) message.getReplyMarkup();
        assertThat(markup.getKeyboard().getFirst().getFirst().getText()).isEqualTo("Продукты (55%)");
        assertThat(markup.getKeyboard().get(1).getFirst().getText())
                .isEqualTo("Показать существующие категории");
        assertThat(markup.getKeyboard().get(2).getFirst().getText())
                .isEqualTo("Ввести свою категорию");
    }

    @Test
    void savesExpenseWithExistingCategoryAfterWrongMlSuggestion() throws TelegramApiException {
        CategoryJpa wrongMlCategory = category("Продукты");
        CategoryJpa selectedCategory = category("Подарки");
        List<CategoryJpa> categories = List.of(wrongMlCategory, selectedCategory);
        ExpenseJpa saved = expense(selectedCategory, "250", "цветы");
        when(expenseService.getOrCreateCategories(new ChatId(CHAT_ID), new UserId(USER_ID)))
                .thenReturn(categories);
        when(expenseMlClient.predict(
                eq(new ChatId(CHAT_ID)),
                any(QuickExpense.class),
                eq(categories)
        )).thenReturn(ExpenseCategoryPrediction.reviewOnly(List.of(
                new ExpensePredictionAlternative(
                        wrongMlCategory.getId(),
                        wrongMlCategory.getName(),
                        0.72
                )
        )));
        when(expenseService.addSpending(
                eq(new UserId(USER_ID)),
                eq(new ChatId(CHAT_ID)),
                any()
        )).thenReturn(saved);
        when(expenseService.getStatus(new ChatId(CHAT_ID), new UserId(USER_ID)))
                .thenReturn(status("250", selectedCategory.getName()));
        UserSession session = new UserSession();

        service.tryStartQuickExpense(messageUpdate("цветы 250"), session);

        InlineKeyboardMarkup mlMarkup = (InlineKeyboardMarkup) sendMessages(telegramClient)
                .getFirst()
                .getReplyMarkup();
        assertThat(mlMarkup.getKeyboard().getFirst().getFirst().getText())
                .isEqualTo("Продукты (72%)");
        assertThat(mlMarkup.getKeyboard().get(1).getFirst().getCallbackData())
                .isEqualTo(QuickExpenseFlowService.QUICK_SHOW_CATEGORIES_CALLBACK);

        service.continueQuickExpense(
                callbackUpdate(QuickExpenseFlowService.QUICK_SHOW_CATEGORIES_CALLBACK),
                session
        );

        InlineKeyboardMarkup existingMarkup = (InlineKeyboardMarkup) sendMessages(telegramClient)
                .get(1)
                .getReplyMarkup();
        assertThat(existingMarkup.getKeyboard().get(1).getFirst().getText())
                .isEqualTo("Подарки");
        assertThat(existingMarkup.getKeyboard().get(1).getFirst().getCallbackData())
                .isEqualTo(selectedCategory.getId().getId().toString());

        service.continueQuickExpense(
                callbackUpdate(selectedCategory.getId().getId().toString()),
                session
        );

        ArgumentCaptor<UserSession> savedSession = ArgumentCaptor.forClass(UserSession.class);
        verify(expenseService).addSpending(
                eq(new UserId(USER_ID)),
                eq(new ChatId(CHAT_ID)),
                savedSession.capture()
        );
        assertThat(savedSession.getValue().getCategoryId().getId())
                .isEqualTo(selectedCategory.getId().getId());
        assertThat(savedSession.getValue().getAmount()).isEqualByComparingTo("250");
        assertThat(savedSession.getValue().getDescription()).isEqualTo("цветы");
        assertThat(session.getStep()).isEqualTo(Step.DONE);
        assertThat(sendMessages(telegramClient).getLast().getText())
                .contains("✓ 250 · Подарки · цветы");
    }

    @Test
    void createsCategoryAndSavesExpenseAfterWrongMlSuggestion() throws TelegramApiException {
        CategoryJpa wrongMlCategory = category("Продукты");
        CategoryJpa transport = category("Транспорт");
        List<CategoryJpa> categories = List.of(wrongMlCategory, transport);
        CategoryJpa newCategory = category("Флористика");
        ExpenseJpa saved = expense(newCategory, "250", "цветы");
        when(expenseService.getOrCreateCategories(new ChatId(CHAT_ID), new UserId(USER_ID)))
                .thenReturn(categories);
        when(expenseMlClient.predict(
                eq(new ChatId(CHAT_ID)),
                any(QuickExpense.class),
                eq(categories)
        )).thenReturn(ExpenseCategoryPrediction.reviewOnly(List.of(
                new ExpensePredictionAlternative(
                        wrongMlCategory.getId(),
                        wrongMlCategory.getName(),
                        0.72
                )
        )));
        when(expenseService.createCategory(
                new ChatId(CHAT_ID),
                new UserId(USER_ID),
                "Флористика"
        )).thenReturn(newCategory);
        when(expenseService.addSpending(
                eq(new UserId(USER_ID)),
                eq(new ChatId(CHAT_ID)),
                any()
        )).thenReturn(saved);
        when(expenseService.getStatus(new ChatId(CHAT_ID), new UserId(USER_ID)))
                .thenReturn(status("250", newCategory.getName()));
        UserSession session = new UserSession();

        service.tryStartQuickExpense(messageUpdate("цветы 250"), session);
        service.continueQuickExpense(
                callbackUpdate(QuickExpenseFlowService.QUICK_SHOW_CATEGORIES_CALLBACK),
                session
        );

        InlineKeyboardMarkup existingMarkup = (InlineKeyboardMarkup) sendMessages(telegramClient)
                .get(1)
                .getReplyMarkup();
        assertThat(existingMarkup.getKeyboard().getLast().getFirst().getText())
                .isEqualTo("Ввести новую категорию");
        assertThat(existingMarkup.getKeyboard().getLast().getFirst().getCallbackData())
                .isEqualTo(QuickExpenseFlowService.QUICK_CUSTOM_CATEGORY_CALLBACK);

        service.continueQuickExpense(
                callbackUpdate(QuickExpenseFlowService.QUICK_CUSTOM_CATEGORY_CALLBACK),
                session
        );
        assertThat(session.getStep()).isEqualTo(Step.AWAITING_QUICK_EXPENSE_CATEGORY_NAME);

        service.continueQuickExpense(messageUpdate("Флористика"), session);

        verify(expenseService).createCategory(
                new ChatId(CHAT_ID),
                new UserId(USER_ID),
                "Флористика"
        );
        ArgumentCaptor<UserSession> savedSession = ArgumentCaptor.forClass(UserSession.class);
        verify(expenseService).addSpending(
                eq(new UserId(USER_ID)),
                eq(new ChatId(CHAT_ID)),
                savedSession.capture()
        );
        assertThat(savedSession.getValue().getCategoryId().getId())
                .isEqualTo(newCategory.getId().getId());
        assertThat(savedSession.getValue().getAmount()).isEqualByComparingTo("250");
        assertThat(savedSession.getValue().getDescription()).isEqualTo("цветы");
        assertThat(session.getStep()).isEqualTo(Step.DONE);
        assertThat(sendMessages(telegramClient).getLast().getText())
                .contains("✓ 250 · Флористика · цветы");
    }

    @Test
    void savesExpenseWithSelectedExistingCategory() throws TelegramApiException {
        CategoryJpa products = category("Продукты");
        ExpenseJpa saved = expense(products, "250", "цветы");
        when(expenseService.addSpending(eq(new UserId(USER_ID)), eq(new ChatId(CHAT_ID)), any()))
                .thenReturn(saved);
        when(expenseService.getStatus(new ChatId(CHAT_ID), new UserId(USER_ID)))
                .thenReturn(status("250", "Продукты"));
        UserSession session = reviewSession();

        service.continueQuickExpense(callbackUpdate(products.getId().getId().toString()), session);

        assertThat(session.getStep()).isEqualTo(Step.DONE);
        assertThat(session.getCategoryId().getId()).isEqualTo(products.getId().getId());
        assertThat(sendMessages(telegramClient).getFirst().getText()).contains("✓ 250 · Продукты · цветы");
    }

    @Test
    void createsCategoryInsideExpenseAndSavesOriginalInput() throws TelegramApiException {
        CategoryJpa pets = category("Питомец");
        ExpenseJpa saved = expense(pets, "250", "корм коту");
        when(expenseService.createCategory(new ChatId(CHAT_ID), new UserId(USER_ID), "Питомец"))
                .thenReturn(pets);
        when(expenseService.addSpending(eq(new UserId(USER_ID)), eq(new ChatId(CHAT_ID)), any()))
                .thenReturn(saved);
        when(expenseService.getStatus(new ChatId(CHAT_ID), new UserId(USER_ID)))
                .thenReturn(status("250", "Питомец"));
        UserSession session = reviewSession();
        session.setDescription("корм коту");
        session.setStep(Step.AWAITING_QUICK_EXPENSE_CATEGORY_NAME);

        service.continueQuickExpense(messageUpdate("Питомец"), session);

        assertThat(session.getStep()).isEqualTo(Step.DONE);
        assertThat(session.getCategoryId()).isEqualTo(pets.getId());
        assertThat(sendMessages(telegramClient).getFirst().getText()).contains("✓ 250 · Питомец · корм коту");
    }

    private UserSession reviewSession() {
        UserSession session = new UserSession();
        session.setStep(Step.AWAITING_QUICK_EXPENSE_CATEGORY);
        session.setFlow(FlowType.QUICK_EXPENSE);
        session.setRawText("цветы 250");
        session.setAmount(new BigDecimal("250"));
        session.setDescription("цветы");
        return session;
    }

    private SpendingStatus status(String spent, String category) {
        return new SpendingStatus(
                new BigDecimal("1000"),
                new BigDecimal(spent),
                Map.of(category, new BigDecimal(spent))
        );
    }

    private ExpenseJpa expense(CategoryJpa category, String amount, String description) {
        ExpenseJpa expense = new ExpenseJpa();
        expense.setId(new ExpenseId(UUID.randomUUID()));
        expense.setChat(category.getChat());
        expense.setCategory(category);
        expense.setAmount(new BigDecimal(amount));
        expense.setDescription(description);
        return expense;
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
