package com.cpallas.expenses.service.flow;

import com.cpallas.expenses.UserSession;
import com.cpallas.expenses.enums.FlowType;
import com.cpallas.expenses.enums.Step;
import com.cpallas.expenses.service.ExpenseService;
import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.ids.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import static com.cpallas.expenses.service.flow.FlowTestSupport.callbackUpdate;
import static com.cpallas.expenses.service.flow.FlowTestSupport.messageUpdate;
import static com.cpallas.expenses.service.flow.FlowTestSupport.sendMessages;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AddCategoryFlowServiceTest {

    @Mock
    private TelegramClient telegramClient;
    @Mock
    private ExpenseService expenseService;

    private AddCategoryFlowService service;

    @BeforeEach
    void setUp() {
        service = new AddCategoryFlowService(telegramClient, expenseService);
    }

    @Test
    void asksForCategoryNameOnStart() throws TelegramApiException {
        UserSession session = new UserSession();
        session.setStep(Step.START_ADD_CATEGORY);

        service.handle(callbackUpdate(Step.START_ADD_CATEGORY.name()), session);

        assertThat(session.getStep()).isEqualTo(Step.AWAITING_CATEGORY_NAME);
        assertThat(session.getFlow()).isEqualTo(FlowType.ADD_CATEGORY);
        assertThat(sendMessages(telegramClient).getFirst().getText()).isEqualTo("Введите название категории");
    }

    @Test
    void createsCategoryAndCompletesFlow() throws TelegramApiException {
        UserSession session = new UserSession();
        session.setStep(Step.AWAITING_CATEGORY_NAME);

        service.handle(messageUpdate("Кофе"), session);

        verify(expenseService).createCategory(eq(new ChatId(FlowTestSupport.CHAT_ID)), eq(new UserId(FlowTestSupport.USER_ID)), eq("Кофе"));
        assertThat(session.getStep()).isEqualTo(Step.DONE);
        assertThat(session.getFlow()).isEqualTo(FlowType.ADD_CATEGORY);

        SendMessage message = sendMessages(telegramClient).getFirst();
        assertThat(message.getText()).isEqualTo("Категория успешно добавлена");
        assertThat(message.getReplyMarkup()).isNotNull();
    }
}
