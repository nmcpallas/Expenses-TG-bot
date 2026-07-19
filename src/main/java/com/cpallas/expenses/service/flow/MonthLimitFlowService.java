package com.cpallas.expenses.service.flow;

import com.cpallas.expenses.UserSession;
import com.cpallas.expenses.enums.FlowType;
import com.cpallas.expenses.enums.Step;
import com.cpallas.expenses.exception.WrongFormat;
import com.cpallas.expenses.service.ExpenseService;
import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.ids.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

import static com.cpallas.expenses.controller.util.MessageUtil.createBtn;
import static com.cpallas.expenses.controller.util.MessageUtil.createMessage;

@Service
@RequiredArgsConstructor
public class MonthLimitFlowService {

    private final TelegramClient telegramClient;
    private final ExpenseService expenseService;

    public void handle(Update update, UserSession session) throws TelegramApiException {
        switch (session.getStep()) {
            case START_SET_MONTH_LIMIT -> waitForMonthLimit(update, session);
            case AWAITING_MONTH_LIMIT -> addMonthLimit(update, session);
            default -> throw new IllegalStateException("Unsupported month limit step: " + session.getStep());
        }
    }

    private void waitForMonthLimit(Update update, UserSession session) throws TelegramApiException {
        session.setStep(Step.AWAITING_MONTH_LIMIT);
        session.setFlow(FlowType.SET_MONTH_LIMIT);
        telegramClient.execute(createMessage("Отправьте сумму ограничения", getChatIdFromUpdate(update)));
    }

    private void addMonthLimit(Update update, UserSession session) throws TelegramApiException {
        try {
            expenseService.setOrUpdateLimitation(
                    getUserIdFromUpdate(update),
                    new ChatId(getChatIdFromUpdate(update)),
                    update.getMessage().getText()
            );
            session.setStep(Step.DONE);
            session.setFlow(FlowType.SET_MONTH_LIMIT);
            SendMessage message = createMessage("Ограничение успешно установлено", getChatIdFromUpdate(update));
            message.setReplyMarkup(backToMenuMarkup());
            telegramClient.execute(message);
        } catch (WrongFormat e) {
            telegramClient.execute(createMessage("Ошибка в формате суммы ограничения. Используйте, пожалуйста, только цифры. Попробуйте еще раз",
                    getChatIdFromUpdate(update)));
        }
    }

    private InlineKeyboardMarkup backToMenuMarkup() {
        return new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(createBtn("Назад к главному меню", Step.SHOW_GENERAL_MENU.name()))));
    }

    private Long getChatIdFromUpdate(Update update) {
        if (update.hasMessage()) return update.getMessage().getChatId();
        return update.getCallbackQuery().getMessage().getChatId();
    }

    private UserId getUserIdFromUpdate(Update update) {
        if (update.hasMessage()) return new UserId(update.getMessage().getFrom().getId());
        return new UserId(update.getCallbackQuery().getFrom().getId());
    }
}
