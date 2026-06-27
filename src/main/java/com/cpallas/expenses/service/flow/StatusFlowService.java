package com.cpallas.expenses.service.flow;

import com.cpallas.expenses.UserSession;
import com.cpallas.expenses.enums.FlowType;
import com.cpallas.expenses.enums.Step;
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
public class StatusFlowService {

    private final TelegramClient telegramClient;
    private final ExpenseService expenseService;

    public void handle(Update update, UserSession session) throws TelegramApiException {
        if (session.getStep() != Step.SHOW_CURRENT_STATUS) {
            throw new IllegalStateException("Unsupported status step: " + session.getStep());
        }

        String status = expenseService.getStatus(
                        new ChatId(getChatIdFromUpdate(update)),
                        getUserIdFromUpdate(update)
                )
                .getStatus();
        session.setStep(Step.DONE);
        session.setFlow(FlowType.GENERAL_MENU);
        SendMessage message = createMessage(status, getChatIdFromUpdate(update));
        message.setReplyMarkup(backToMenuMarkup());
        telegramClient.execute(message);
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
