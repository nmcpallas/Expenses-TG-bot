package com.cpallas.expenses.service.flow;

import com.cpallas.expenses.enums.FlowType;
import com.cpallas.expenses.enums.Step;
import org.springframework.stereotype.Component;

@Component
public class FlowTypeResolver {

    public FlowType resolve(Step step) {
        return switch (step) {
            case START_ADD_EXPENSE,
                 AWAITING_EXPENSE_AMOUNT,
                 AWAITING_EXPENSE_CATEGORY,
                 AWAITING_EXPENSE_DESCRIPTION -> FlowType.ADD_EXPENSE;
            case START_QUICK_EXPENSE,
                 AWAITING_QUICK_EXPENSE_CATEGORY,
                 AWAITING_QUICK_EXPENSE_CATEGORY_NAME -> FlowType.QUICK_EXPENSE;
            case START_ADD_CATEGORY,
                 AWAITING_CATEGORY_NAME -> FlowType.ADD_CATEGORY;
            case START_SET_MONTH_LIMIT,
                 AWAITING_MONTH_LIMIT -> FlowType.SET_MONTH_LIMIT;
            case START_SET_MONTH_START_DAY,
                 AWAITING_MONTH_START_DAY -> FlowType.SET_MONTH_START;
            case START_DOWNLOAD_EXCEL,
                 AWAITING_EXCEL_MONTH -> FlowType.DOWNLOAD_EXCEL;
            case SHOW_CURRENT_STATUS,
                 SHOW_GENERAL_MENU,
                 DONE -> FlowType.GENERAL_MENU;
        };
    }
}
