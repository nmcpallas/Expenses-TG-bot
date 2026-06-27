package com.cpallas.expenses.service.flow;

import com.cpallas.expenses.UserSession;
import com.cpallas.expenses.controller.dto.CalendarMenu;
import com.cpallas.expenses.controller.dto.Month;
import com.cpallas.expenses.enums.FlowType;
import com.cpallas.expenses.enums.Step;
import com.cpallas.expenses.service.ExpenseExcelExporter;
import com.cpallas.expenses.service.ExpenseService;
import com.cpallas.expenses.storage.ids.ChatId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;

import static com.cpallas.expenses.controller.util.MessageUtil.createMessage;

@Service
@RequiredArgsConstructor
public class ExcelExportFlowService {

    private static final InlineKeyboardMarkup CALENDAR_MENU_MARKUP = CalendarMenu.init();

    private final TelegramClient telegramClient;
    private final ExpenseService expenseService;

    public void handle(Update update, UserSession session) throws TelegramApiException {
        switch (session.getStep()) {
            case START_DOWNLOAD_EXCEL -> waitForDatePeriod(update, session);
            case AWAITING_EXCEL_MONTH -> downloadExcelFile(update, session);
            default -> throw new IllegalStateException("Unsupported excel export step: " + session.getStep());
        }
    }

    private void waitForDatePeriod(Update update, UserSession session) throws TelegramApiException {
        session.setStep(Step.AWAITING_EXCEL_MONTH);
        session.setFlow(FlowType.DOWNLOAD_EXCEL);
        SendMessage message = createMessage("Выберите месяц в текущем году", getChatIdFromUpdate(update));
        message.setReplyMarkup(CALENDAR_MENU_MARKUP);
        telegramClient.execute(message);
    }

    private void downloadExcelFile(Update update, UserSession session) throws TelegramApiException {
        if (!update.hasCallbackQuery()) {
            SendMessage message = createMessage("Выберите месяц в текущем году", getChatIdFromUpdate(update));
            message.setReplyMarkup(CALENDAR_MENU_MARKUP);
            telegramClient.execute(message);
        }
        byte[] excelBytes = ExpenseExcelExporter.exportExpensesToExcel(
                expenseService.getExpenses(
                        new ChatId(getChatIdFromUpdate(update)),
                        Month.valueOf(update.getCallbackQuery().getData())
                )
        );

        ByteArrayInputStream inputStream = new ByteArrayInputStream(excelBytes);
        InputFile inputFile = new InputFile(inputStream, "expenses.xlsx");

        SendDocument sendDocument = SendDocument.builder()
                .chatId(getChatIdFromUpdate(update))
                .document(inputFile)
                .caption("Вот ваши расходы в формате Excel")
                .build();
        telegramClient.execute(sendDocument);
    }

    private Long getChatIdFromUpdate(Update update) {
        if (update.hasMessage()) return update.getMessage().getChatId();
        return update.getCallbackQuery().getMessage().getChatId();
    }
}
