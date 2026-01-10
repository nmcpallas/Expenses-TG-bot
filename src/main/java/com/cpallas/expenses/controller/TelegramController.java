package com.cpallas.expenses.controller;

import com.cpallas.expenses.controller.consumer.UpdateConsumer;
import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;

@RequiredArgsConstructor
public class TelegramController implements SpringLongPollingBot {

    private final String token;
    private final UpdateConsumer consumer;

    @Override
    public String getBotToken() {
        return token;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return consumer;
    }
}
