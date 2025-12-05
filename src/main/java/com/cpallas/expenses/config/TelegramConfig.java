package com.cpallas.expenses.config;

import com.cpallas.expenses.controller.consumer.UpdateConsumer;
import com.cpallas.expenses.controller.TelegramController;
import com.cpallas.expenses.controller.handler.ChatNotifier;
import com.cpallas.expenses.controller.handler.UpdateHandler;
import com.cpallas.expenses.controller.process.ChatUpdateDispatcher;
import com.cpallas.expenses.service.ExpenseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration
public class TelegramConfig {

    @Bean
    public TelegramClient telegramClient(@Value("${telegram.bot.token}") String botToken) {
        return new OkHttpTelegramClient(botToken);
    }

    @Bean
    public UpdateHandler updateHandler(ExpenseService expenseService, TelegramClient telegramClient) {
        return new UpdateHandler(telegramClient, expenseService);
    }

    @Bean
    public ChatNotifier chatNotifier(TelegramClient telegramClient) {
        return new ChatNotifier(telegramClient);
    }

    @Bean
    public ChatUpdateDispatcher virtualThreadUpdateDispatcher(@Value("${system.dispatcher-capacity}") Integer capacity,
                                                              @Value("${system.chat-blocking-duration}") Integer blockingTime, UpdateHandler updateHandler,
                                                              ChatNotifier chatNotifier) {
        return new ChatUpdateDispatcher(capacity, blockingTime, updateHandler, chatNotifier);
    }

    @Bean
    public UpdateConsumer updateConsumer(ChatUpdateDispatcher dispatcher) {
        return new UpdateConsumer(dispatcher);
    }

    @Bean
    public TelegramController telegramController(@Value("${telegram.bot.token}")String token, UpdateConsumer consumer) {
        return new TelegramController(token, consumer);
    }
}
