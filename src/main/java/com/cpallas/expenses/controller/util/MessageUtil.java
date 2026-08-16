package com.cpallas.expenses.controller.util;

import lombok.NoArgsConstructor;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class MessageUtil {

    public static SendMessage createMessage(String text, Long chatId) {
        return SendMessage.builder()
                .text(text)
                .chatId(chatId)
                .build();
    }

    public static InlineKeyboardButton createBtn(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }

    public static InlineKeyboardButton createWebAppBtn(String text, String url) {
        return InlineKeyboardButton.builder()
                .text(text)
                .webApp(WebAppInfo.builder().url(url).build())
                .build();
    }

    public static InlineKeyboardButton createUrlBtn(String text, String url) {
        return InlineKeyboardButton.builder()
                .text(text)
                .url(url)
                .build();
    }
}
