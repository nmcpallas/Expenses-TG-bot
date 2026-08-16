package com.cpallas.expenses.admin;

import com.cpallas.expenses.storage.repo.ChatRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.ArrayList;
import java.util.List;

import static com.cpallas.expenses.controller.util.MessageUtil.createMessage;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminBroadcastService {

    private static final int TELEGRAM_MESSAGE_MAX_LENGTH = 4096;

    private final ChatRepo chatRepo;
    private final TelegramClient telegramClient;

    public AdminBroadcastDtos.Result broadcast(String text) {
        String message = validatedMessage(text);
        List<Long> chatIds = chatRepo.findAllChatIds();
        List<AdminBroadcastDtos.Failure> failures = new ArrayList<>();
        int sent = 0;

        for (Long chatId : chatIds) {
            try {
                telegramClient.execute(createMessage(message, chatId));
                sent++;
            } catch (Exception exception) {
                String error = errorMessage(exception);
                failures.add(new AdminBroadcastDtos.Failure(chatId, error));
                log.warn("Admin broadcast delivery failed: chatId={}, reason={}", chatId, error);
            }
        }

        log.info(
                "Admin broadcast completed: totalChats={}, sent={}, failed={}",
                chatIds.size(),
                sent,
                failures.size()
        );
        return new AdminBroadcastDtos.Result(
                chatIds.size(),
                sent,
                failures.size(),
                List.copyOf(failures)
        );
    }

    private String validatedMessage(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Broadcast text must not be blank.");
        }
        String message = text.strip();
        if (message.length() > TELEGRAM_MESSAGE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Broadcast text must not exceed %d characters.".formatted(TELEGRAM_MESSAGE_MAX_LENGTH)
            );
        }
        return message;
    }

    private String errorMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
