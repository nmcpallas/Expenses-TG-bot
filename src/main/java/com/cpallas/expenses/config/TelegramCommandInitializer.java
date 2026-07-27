package com.cpallas.expenses.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.menubutton.SetChatMenuButton;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.menubutton.MenuButtonCommands;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Slf4j
@Component
public class TelegramCommandInitializer {

    private final TelegramClient telegramClient;
    private final boolean telegramEnabled;

    public TelegramCommandInitializer(
            TelegramClient telegramClient,
            @Value("${telegram.enabled:true}") boolean telegramEnabled
    ) {
        this.telegramClient = telegramClient;
        this.telegramEnabled = telegramEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerCommands() {
        if (!telegramEnabled) {
            return;
        }
        try {
            telegramClient.execute(SetMyCommands.builder()
                    .command(BotCommand.builder()
                            .command("help")
                            .description("Как пользоваться ботом")
                            .build())
                    .build());
            telegramClient.execute(SetChatMenuButton.builder()
                    .menuButton(MenuButtonCommands.builder().build())
                    .build());
        } catch (Exception exception) {
            log.warn("Unable to register Telegram help command", exception);
        }
    }
}
