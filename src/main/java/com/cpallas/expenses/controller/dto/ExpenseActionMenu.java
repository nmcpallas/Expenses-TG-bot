package com.cpallas.expenses.controller.dto;

import com.cpallas.expenses.storage.ids.ExpenseId;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.List;

import static com.cpallas.expenses.controller.util.MessageUtil.createBtn;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExpenseActionMenu {

    public static final String EDIT_PREFIX = "ee:";
    public static final String DELETE_PREFIX = "ed:";
    public static final String EDIT_AMOUNT_PREFIX = "ea:";
    public static final String EDIT_DESCRIPTION_PREFIX = "et:";
    public static final String EDIT_CATEGORY_PREFIX = "ec:";
    public static final String SELECT_CATEGORY_PREFIX = "es:";
    public static final String NEW_CATEGORY = "en";

    public static InlineKeyboardMarkup afterSave(ExpenseId expenseId) {
        return new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(
                createBtn("Изменить", EDIT_PREFIX + expenseId.getId()),
                createBtn("Отменить", DELETE_PREFIX + expenseId.getId())
        )));
    }

    public static InlineKeyboardMarkup edit(ExpenseId expenseId) {
        return new InlineKeyboardMarkup(List.of(
                new InlineKeyboardRow(createBtn("Сумму", EDIT_AMOUNT_PREFIX + expenseId.getId())),
                new InlineKeyboardRow(createBtn("Категорию", EDIT_CATEGORY_PREFIX + expenseId.getId())),
                new InlineKeyboardRow(createBtn("Описание", EDIT_DESCRIPTION_PREFIX + expenseId.getId())),
                new InlineKeyboardRow(createBtn("Удалить трату", DELETE_PREFIX + expenseId.getId()))
        ));
    }
}
