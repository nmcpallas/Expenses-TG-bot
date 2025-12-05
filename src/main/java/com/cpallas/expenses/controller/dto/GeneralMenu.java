package com.cpallas.expenses.controller.dto;

import com.cpallas.expenses.Step;
import lombok.NoArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.List;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class GeneralMenu {

    public static InlineKeyboardMarkup init() {
        InlineKeyboardRow row1 = new InlineKeyboardRow();
        row1.addAll(List.of(
                createBtn("Ввести одну трату", Step.SAVING_EXPENSE),
                createBtn("Статистика", Step.GETTING_STATISTICS)
        ));
        InlineKeyboardRow row2 = new InlineKeyboardRow();
        row2.add(createBtn("Добавить месячное ограничение", Step.ADDING_MONTH_LIMITATION));
        List<InlineKeyboardRow> keyboard = List.of(row1, row2);
        return new InlineKeyboardMarkup(keyboard);
    }

    private static InlineKeyboardButton createBtn(String text, Step callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData.name())
                .build();
    }
}
