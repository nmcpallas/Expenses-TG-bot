package com.cpallas.expenses.miniapp;

import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.ids.UserId;

public record TelegramMiniAppPrincipal(
        UserId userId,
        ChatId chatId,
        String firstName,
        String username
) {
}
