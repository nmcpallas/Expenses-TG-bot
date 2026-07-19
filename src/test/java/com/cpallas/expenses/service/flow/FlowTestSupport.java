package com.cpallas.expenses.service.flow;

import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

public final class FlowTestSupport {

    public static final Long CHAT_ID = 1L;
    public static final Long USER_ID = 2L;

    private FlowTestSupport() {
    }

    public static Update messageUpdate(String text) {
        Update update = new Update();
        Message message = new Message();
        message.setChat(new Chat(CHAT_ID, "test"));
        message.setFrom(new User(USER_ID, "test", false));
        message.setText(text);
        update.setMessage(message);
        return update;
    }

    public static Update callbackUpdate(String data) {
        Update update = new Update();
        CallbackQuery callbackQuery = new CallbackQuery();
        Message message = new Message();
        message.setChat(new Chat(CHAT_ID, "test"));
        callbackQuery.setFrom(new User(USER_ID, "test", false));
        callbackQuery.setMessage(message);
        callbackQuery.setData(data);
        update.setCallbackQuery(callbackQuery);
        return update;
    }

    public static List<SendMessage> sendMessages(TelegramClient telegramClient) throws TelegramApiException {
        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());
        return captor.getAllValues();
    }
}
