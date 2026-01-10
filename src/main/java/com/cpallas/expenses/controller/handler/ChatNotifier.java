package com.cpallas.expenses.controller.handler;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.Duration;
import java.time.Instant;

import static com.cpallas.expenses.controller.util.MessageUtil.createMessage;

@RequiredArgsConstructor
public class ChatNotifier {

    private final TelegramClient telegramClient;

    public void notifyBlocked(Long chatId, Instant blockedUntil) throws TelegramApiException {
        telegramClient.execute(createMessage("Вы заблокированы на: %d минут".formatted(Duration.between(Instant.now(), blockedUntil).toMinutes()), chatId));
    }
}
