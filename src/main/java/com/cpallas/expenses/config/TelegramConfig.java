package com.cpallas.expenses.config;

import com.cpallas.expenses.controller.consumer.UpdateConsumer;
import com.cpallas.expenses.controller.TelegramController;
import com.cpallas.expenses.controller.handler.ChatNotifier;
import com.cpallas.expenses.controller.handler.UpdateHandler;
import com.cpallas.expenses.controller.process.ChatUpdateDispatcher;
import com.cpallas.expenses.miniapp.MiniAppLaunchContextService;
import com.cpallas.expenses.service.flow.ExpenseActionFlowService;
import com.cpallas.expenses.service.ml.QuickExpenseFlowService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
    public UpdateHandler updateHandler(TelegramClient telegramClient,
                                       QuickExpenseFlowService quickExpenseFlowService,
                                       ExpenseActionFlowService expenseActionFlowService,
                                       @Value("${expense.mini-app.url:}") String miniAppUrl,
                                       @Value("${telegram.bot.username:}") String botUsername,
                                       MiniAppLaunchContextService launchContextService) {
        return new UpdateHandler(
                telegramClient,
                quickExpenseFlowService,
                expenseActionFlowService,
                miniAppUrl,
                botUsername,
                launchContextService
        );
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
    @ConditionalOnProperty(
            name = "telegram.enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public TelegramController telegramController(@Value("${telegram.bot.token}") String token,
                                                  UpdateConsumer consumer) {
        return new TelegramController(token, consumer);
    }
}
