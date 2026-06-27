package com.cpallas.expenses.service.ml;

import com.cpallas.expenses.UserSession;
import com.cpallas.expenses.service.ExpenseService;
import com.cpallas.expenses.service.dto.ExpenseCategoryPrediction;
import com.cpallas.expenses.storage.ids.CategoryId;
import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.ids.UserId;
import com.cpallas.expenses.storage.jpa.CategoryJpa;
import com.cpallas.expenses.storage.jpa.ChatJpa;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QuickExpenseFlowServiceTest {

    @Mock
    private TelegramClient telegramClient;
    @Mock
    private ExpenseService expenseService;
    @Mock
    private ExpenseMlClient expenseMlClient;

    @Test
    void quickExpenseIsSavedWhenMlConfidenceIsEnough() throws Exception {
        CategoryJpa category = category("Кафе");
        Mockito.when(expenseService.getCategories(any(ChatId.class))).thenReturn(List.of(category));
        Mockito.when(expenseMlClient.predict(any(), any(), any()))
                .thenReturn(new ExpenseCategoryPrediction(category.getId(), category.getName(), 0.91, false, List.of()));
        QuickExpenseFlowService service = new QuickExpenseFlowService(
                telegramClient,
                expenseService,
                expenseMlClient
        );

        boolean handled = service.tryStartQuickExpense(textUpdate("250 кофе"), new UserSession());

        ArgumentCaptor<UserSession> sessionCaptor = ArgumentCaptor.forClass(UserSession.class);
        verify(expenseService).addSpending(any(UserId.class), any(ChatId.class), sessionCaptor.capture());
        assertThat(handled).isTrue();
        assertThat(sessionCaptor.getValue().getAmount()).isEqualByComparingTo("250");
        assertThat(sessionCaptor.getValue().getDescription()).isEqualTo("кофе");
        assertThat(sessionCaptor.getValue().getCategoryId()).isEqualTo(category.getId());
        assertThat(getSendMessage().getText()).isEqualTo("Трата сохранена: 250 · Кафе · кофе");
    }

    private CategoryJpa category(String name) {
        ChatJpa chat = new ChatJpa();
        chat.setId(new ChatId(1L));

        CategoryJpa category = new CategoryJpa();
        category.setId(new CategoryId(UUID.randomUUID()));
        category.setChat(chat);
        category.setName(name);
        return category;
    }

    private Update textUpdate(String text) {
        Message message = new Message();
        message.setText(text);
        message.setChat(new Chat(1L, "test"));
        message.setFrom(new User(1L, "test", false));

        Update update = new Update();
        update.setMessage(message);
        return update;
    }

    private SendMessage getSendMessage() throws Exception {
        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        return captor.getValue();
    }
}
