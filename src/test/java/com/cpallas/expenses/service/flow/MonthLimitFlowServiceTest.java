package com.cpallas.expenses.service.flow;

import com.cpallas.expenses.UserSession;
import com.cpallas.expenses.enums.FlowType;
import com.cpallas.expenses.enums.Step;
import com.cpallas.expenses.exception.WrongFormat;
import com.cpallas.expenses.service.ExpenseService;
import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.ids.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import static com.cpallas.expenses.service.flow.FlowTestSupport.callbackUpdate;
import static com.cpallas.expenses.service.flow.FlowTestSupport.messageUpdate;
import static com.cpallas.expenses.service.flow.FlowTestSupport.sendMessages;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MonthLimitFlowServiceTest {

    @Mock
    private TelegramClient telegramClient;
    @Mock
    private ExpenseService expenseService;

    private MonthLimitFlowService service;

    @BeforeEach
    void setUp() {
        service = new MonthLimitFlowService(telegramClient, expenseService);
    }

    @Test
    void asksForMonthLimitOnStart() throws TelegramApiException {
        UserSession session = new UserSession();
        session.setStep(Step.START_SET_MONTH_LIMIT);

        service.handle(callbackUpdate(Step.START_SET_MONTH_LIMIT.name()), session);

        assertThat(session.getStep()).isEqualTo(Step.AWAITING_MONTH_LIMIT);
        assertThat(session.getFlow()).isEqualTo(FlowType.SET_MONTH_LIMIT);
        assertThat(sendMessages(telegramClient).getFirst().getText()).isEqualTo("Отправьте сумму ограничения");
    }

    @Test
    void savesMonthLimitAndCompletesFlow() throws TelegramApiException, WrongFormat {
        UserSession session = new UserSession();
        session.setStep(Step.AWAITING_MONTH_LIMIT);

        service.handle(messageUpdate("1000"), session);

        verify(expenseService).setOrUpdateLimitation(eq(new UserId(FlowTestSupport.USER_ID)), eq(new ChatId(FlowTestSupport.CHAT_ID)), eq("1000"));
        assertThat(session.getStep()).isEqualTo(Step.DONE);
        assertThat(session.getFlow()).isEqualTo(FlowType.SET_MONTH_LIMIT);
        assertThat(sendMessages(telegramClient).getFirst().getText()).isEqualTo("Ограничение успешно установлено");
    }

    @Test
    void keepsStepAndSendsErrorOnWrongFormat() throws TelegramApiException, WrongFormat {
        UserSession session = new UserSession();
        session.setStep(Step.AWAITING_MONTH_LIMIT);
        doThrow(new WrongFormat("bad limit"))
                .when(expenseService)
                .setOrUpdateLimitation(eq(new UserId(FlowTestSupport.USER_ID)), eq(new ChatId(FlowTestSupport.CHAT_ID)), eq("abc"));

        service.handle(messageUpdate("abc"), session);

        assertThat(session.getStep()).isEqualTo(Step.AWAITING_MONTH_LIMIT);
        assertThat(sendMessages(telegramClient).getFirst().getText())
                .isEqualTo("Ошибка в формате суммы ограничения. Используйте, пожалуйста, только цифры. Попробуйте еще раз");
    }
}
