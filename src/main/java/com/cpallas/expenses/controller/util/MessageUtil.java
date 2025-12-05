package com.cpallas.expenses.controller.util;

import lombok.NoArgsConstructor;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class MessageUtil {

    public static SendMessage createMessage(String text, Long chatId) {
        return SendMessage.builder()
                .text(text)
                .chatId(chatId)
                .build();
    }
}
