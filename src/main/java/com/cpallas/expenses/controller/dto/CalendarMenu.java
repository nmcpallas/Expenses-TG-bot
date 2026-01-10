package com.cpallas.expenses.controller.dto;

import lombok.NoArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.cpallas.expenses.controller.util.MessageUtil.createBtn;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class CalendarMenu {

    public static InlineKeyboardMarkup init() {
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        Arrays.stream(Month.values()).forEach($ -> keyboard.add(new InlineKeyboardRow(createBtn($.getDisplayName(), $.name()))));
        return new InlineKeyboardMarkup(keyboard);
    }
}
