package com.cpallas.expenses.service.util;

import com.cpallas.expenses.service.dto.QuickExpense;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public final class QuickExpenseParser {

    private static final Pattern QUICK_EXPENSE_PATTERN = Pattern.compile("^\\s*(\\d+(?:[,.]\\d{1,2})?)\\s+(.+?)\\s*$");

    public static Optional<QuickExpense> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = QUICK_EXPENSE_PATTERN.matcher(text);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        BigDecimal amount = new BigDecimal(matcher.group(1).replace(',', '.'));
        String description = matcher.group(2).trim();
        if (amount.signum() <= 0 || description.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new QuickExpense(text.trim(), amount, description));
    }
}
