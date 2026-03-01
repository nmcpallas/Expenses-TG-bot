package com.cpallas.expenses.controller.dto;

import com.cpallas.expenses.Step;
import lombok.NoArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.Map;

import static com.cpallas.expenses.controller.util.MessageUtil.createBtn;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class GeneralMenu {

    private static final Map<String, Step> kv = Map.of(
            "Ввести одну трату", Step.SAVING_EXPENSE,
            "Текущий статус по тратам", Step.GETTING_CURRENT_STATUS,
            "Добавить месячное ограничение", Step.ADDING_MONTH_LIMITATION,
            "Добавить день начала/конца месяца", Step.INPUT_START_DAY,
            "Добавить категорию", Step.CREATING_EXPENSE_CATEGORY,
            "Получить траты в виде excel-файла", Step.DOWNLOAD_EXCEL_FILE
    );

    public static InlineKeyboardMarkup init() {
        ArrayList<InlineKeyboardRow> rows = new ArrayList<>();
        kv.forEach((k,v) -> rows.add(new InlineKeyboardRow(createBtn(k, v.name()))));
        return new InlineKeyboardMarkup(rows);
    }
}
