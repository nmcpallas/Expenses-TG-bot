package com.cpallas.expenses.controller.dto;

import com.cpallas.expenses.Step;
import com.cpallas.expenses.storage.jpa.CategoryJpa;
import lombok.NoArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;

import static com.cpallas.expenses.controller.util.MessageUtil.createBtn;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class CategoryMenu {

    public static InlineKeyboardMarkup init(List<CategoryJpa> categories) {
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        categories.forEach($ -> keyboard.add(new InlineKeyboardRow(createBtn($.getName(), $.getId().getId().toString()))));
        keyboard.add(new InlineKeyboardRow(createBtn("Добавить категорию", Step.CREATING_EXPENSE_CATEGORY.name())));
        return new InlineKeyboardMarkup(keyboard);
    }

    public static InlineKeyboardMarkup createCategory() {
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        keyboard.add(new InlineKeyboardRow(createBtn("Добавить категорию", Step.CREATING_EXPENSE_CATEGORY.name())));
        return new InlineKeyboardMarkup(keyboard);
    }
}
