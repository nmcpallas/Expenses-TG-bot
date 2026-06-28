package com.cpallas.expenses.service.ml;

import com.cpallas.expenses.service.ExpenseService;
import com.cpallas.expenses.storage.ids.ChatId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QuickExpenseEligibilityService {

    private static final int MIN_CATEGORIES_FOR_REVIEW = 2;
    private static final int MIN_EXPENSES_FOR_REVIEW = 10;
    private static final int MIN_CATEGORIES_FOR_AUTO_SAVE = 3;
    private static final int MIN_EXPENSES_FOR_AUTO_SAVE = 30;

    private final ExpenseService expenseService;

    public QuickExpenseMode resolveMode(ChatId chatId, int categoriesCount) {
        long expensesCount = expenseService.countExpenses(chatId);

        if (categoriesCount < MIN_CATEGORIES_FOR_REVIEW || expensesCount < MIN_EXPENSES_FOR_REVIEW) {
            return QuickExpenseMode.DISABLED;
        }
        if (categoriesCount < MIN_CATEGORIES_FOR_AUTO_SAVE || expensesCount < MIN_EXPENSES_FOR_AUTO_SAVE) {
            return QuickExpenseMode.REVIEW_ONLY;
        }
        return QuickExpenseMode.AUTO_SAVE_ALLOWED;
    }
}
