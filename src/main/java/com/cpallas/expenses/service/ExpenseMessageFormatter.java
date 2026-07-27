package com.cpallas.expenses.service;

import com.cpallas.expenses.controller.dto.SpendingStatus;
import com.cpallas.expenses.storage.jpa.ExpenseJpa;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ExpenseMessageFormatter {

    public String saved(ExpenseJpa expense, SpendingStatus status) {
        String categoryName = expense.getCategory().getName();
        BigDecimal categorySpent = status.spendingByCategories()
                .getOrDefault(categoryName, BigDecimal.ZERO);

        StringBuilder message = new StringBuilder()
                .append("✓ ")
                .append(money(expense.getAmount()))
                .append(" · ")
                .append(categoryName)
                .append(" · ")
                .append(expense.getDescription())
                .append("\n\nВ категории «")
                .append(categoryName)
                .append("» за текущий период: ")
                .append(money(categorySpent))
                .append(categoryLimitText(status, categoryName, categorySpent))
                .append("\nВсего потрачено: ")
                .append(money(status.spent()));

        if (status.monthLimit() != null && status.monthLimit().signum() > 0) {
            BigDecimal remaining = status.monthLimit().subtract(status.spent());
            message.append(" из ").append(money(status.monthLimit()))
                    .append("\nОстаток по плану: ").append(money(remaining));
        }
        return message.toString();
    }

    private String categoryLimitText(SpendingStatus status,
                                     String categoryName,
                                     BigDecimal categorySpent) {
        BigDecimal categoryLimit = status.limitsByCategories().get(categoryName);
        if (categoryLimit == null || categoryLimit.signum() <= 0) {
            return "";
        }
        return " из %s\nОстаток по категории: %s".formatted(
                money(categoryLimit),
                money(categoryLimit.subtract(categorySpent))
        );
    }

    public String deleted(ExpenseJpa expense) {
        return "Отменено: %s · %s · %s".formatted(
                money(expense.getAmount()),
                expense.getCategory().getName(),
                expense.getDescription()
        );
    }

    public String updated(ExpenseJpa expense) {
        return "✓ Трата изменена: %s · %s · %s".formatted(
                money(expense.getAmount()),
                expense.getCategory().getName(),
                expense.getDescription()
        );
    }

    private String money(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }
}
