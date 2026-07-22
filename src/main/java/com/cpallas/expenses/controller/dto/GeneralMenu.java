package com.cpallas.expenses.controller.dto;

import com.cpallas.expenses.enums.Step;
import lombok.NoArgsConstructor;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.Map;

import static com.cpallas.expenses.controller.util.MessageUtil.createBtn;
import static com.cpallas.expenses.controller.util.MessageUtil.createMessage;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class GeneralMenu {

    private static final Map<String, Step> kv = Map.of(
            "Ввести одну трату", Step.START_ADD_EXPENSE,
            "Текущий статус по тратам", Step.SHOW_CURRENT_STATUS,
            "Добавить месячное ограничение", Step.START_SET_MONTH_LIMIT,
            "Добавить день начала/конца месяца", Step.START_SET_MONTH_START_DAY,
            "Добавить категорию", Step.START_ADD_CATEGORY,
            "Получить траты в виде excel-файла", Step.START_DOWNLOAD_EXCEL
    );

    public static InlineKeyboardMarkup init() {
        ArrayList<InlineKeyboardRow> rows = new ArrayList<>();
        kv.forEach((k,v) -> rows.add(new InlineKeyboardRow(createBtn(k, v.name()))));
        return new InlineKeyboardMarkup(rows);
    }

    public static SendMessage createMenuMessage(Long chatId) {
        SendMessage message = createMessage("Выберите дальнейшее действие", chatId);
        message.setReplyMarkup(init());
        return message;
    }
}
