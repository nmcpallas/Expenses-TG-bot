package com.cpallas.expenses.service.flow;

import com.cpallas.expenses.UserSession;
import com.cpallas.expenses.controller.dto.Month;
import com.cpallas.expenses.enums.FlowType;
import com.cpallas.expenses.enums.Step;
import com.cpallas.expenses.service.ExpenseService;
import com.cpallas.expenses.service.dto.ExpenseExportRow;
import com.cpallas.expenses.storage.ids.ChatId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import static com.cpallas.expenses.service.flow.FlowTestSupport.CHAT_ID;
import static com.cpallas.expenses.service.flow.FlowTestSupport.callbackUpdate;
import static com.cpallas.expenses.service.flow.FlowTestSupport.sendMessages;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExcelExportFlowServiceTest {

    @Mock
    private TelegramClient telegramClient;
    @Mock
    private ExpenseService expenseService;

    private ExcelExportFlowService service;

    @BeforeEach
    void setUp() {
        service = new ExcelExportFlowService(telegramClient, expenseService);
    }

    @Test
    void asksForMonthOnStart() throws TelegramApiException {
        UserSession session = new UserSession();
        session.setStep(Step.START_DOWNLOAD_EXCEL);

        service.handle(callbackUpdate(Step.START_DOWNLOAD_EXCEL.name()), session);

        assertThat(session.getStep()).isEqualTo(Step.AWAITING_EXCEL_MONTH);
        assertThat(session.getFlow()).isEqualTo(FlowType.DOWNLOAD_EXCEL);

        SendMessage message = sendMessages(telegramClient).getFirst();
        assertThat(message.getText()).isEqualTo("Выберите месяц в текущем году");
        assertThat(message.getReplyMarkup()).isNotNull();
    }

    @Test
    void sendsExcelDocumentForSelectedMonth() throws TelegramApiException {
        UserSession session = new UserSession();
        session.setStep(Step.AWAITING_EXCEL_MONTH);
        when(expenseService.getExpenses(eq(new ChatId(CHAT_ID)), eq(Month.MARCH)))
                .thenReturn(List.of(new ExpenseExportRow(
                        new BigDecimal("250"),
                        "Кофе",
                        "капучино",
                        ZonedDateTime.now()
                )));

        service.handle(callbackUpdate(Month.MARCH.name()), session);

        verify(expenseService).getExpenses(eq(new ChatId(CHAT_ID)), eq(Month.MARCH));

        ArgumentCaptor<SendDocument> captor = ArgumentCaptor.forClass(SendDocument.class);
        verify(telegramClient).execute(captor.capture());
        SendDocument document = captor.getValue();
        assertThat(document.getCaption()).isEqualTo("Вот ваши расходы в формате Excel");
        assertThat(document.getChatId()).isEqualTo(CHAT_ID.toString());
        assertThat(document.getDocument().getMediaName()).isEqualTo("expenses.xlsx");
    }
}
