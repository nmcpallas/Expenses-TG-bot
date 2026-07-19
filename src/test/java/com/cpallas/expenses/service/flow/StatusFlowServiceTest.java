package com.cpallas.expenses.service.flow;

import com.cpallas.expenses.UserSession;
import com.cpallas.expenses.controller.dto.SpendingStatus;
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

import java.math.BigDecimal;
import java.util.Map;

import static com.cpallas.expenses.service.flow.FlowTestSupport.callbackUpdate;
import static com.cpallas.expenses.service.flow.FlowTestSupport.sendMessages;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatusFlowServiceTest {

    @Mock
    private TelegramClient telegramClient;
    @Mock
    private ExpenseService expenseService;

    private StatusFlowService service;

    @BeforeEach
    void setUp() {
        service = new StatusFlowService(telegramClient, expenseService);
    }

    @Test
    void sendsCurrentStatusAndCompletesFlow() throws TelegramApiException {
        UserSession session = new UserSession();
        session.setStep(Step.SHOW_CURRENT_STATUS);
        when(expenseService.getStatus(eq(new ChatId(FlowTestSupport.CHAT_ID)), eq(new UserId(FlowTestSupport.USER_ID))))
                .thenReturn(new SpendingStatus(new BigDecimal("1000"), new BigDecimal("250"), Map.of("Кофе", new BigDecimal("50"))));

        service.handle(callbackUpdate(Step.SHOW_CURRENT_STATUS.name()), session);

        assertThat(session.getStep()).isEqualTo(Step.DONE);
        assertThat(session.getFlow()).isEqualTo(FlowType.GENERAL_MENU);

        SendMessage message = sendMessages(telegramClient).getFirst();
        assertThat(message.getText())
                .contains("Месячное ограничение: 1000,0")
                .contains("Потрачено на данный момент: 250,0")
                .contains("Кофе: 50,0");
        assertThat(message.getReplyMarkup()).isNotNull();
    }
}
