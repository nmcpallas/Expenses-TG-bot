package com.cpallas.expenses.service.util;

import com.cpallas.expenses.service.dto.QuickExpense;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public final class QuickExpenseParser {

    /*
     * Whitespace is accepted only as a conventional thousands separator.
     * This prevents product names containing a number (for example
     * "айфон 15 100000") from being merged into one 15 100 000 amount.
     */
    private static final String AMOUNT =
            "((?:\\d{1,3}(?:[ \\u00A0]\\d{3})+|\\d+)(?:[,.]\\d{1,2})?)";
    private static final String MULTIPLIER = "(к|k|тыс(?:\\.|яч[аи]?)?)?";
    private static final Pattern AMOUNT_FIRST = Pattern.compile(
            "^\\s*" + AMOUNT + "\\s*" + MULTIPLIER + "\\s+(.+?)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern DESCRIPTION_FIRST = Pattern.compile(
            "^\\s*(.+?)\\s+" + AMOUNT + "\\s*" + MULTIPLIER + "\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    public static Optional<QuickExpense> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        Matcher amountFirst = AMOUNT_FIRST.matcher(text);
        if (amountFirst.matches()) {
            return parsed(
                    text,
                    amountFirst.group(1),
                    amountFirst.group(2),
                    amountFirst.group(3)
            );
        }

        Matcher descriptionFirst = DESCRIPTION_FIRST.matcher(text);
        if (descriptionFirst.matches()) {
            return parsed(
                    text,
                    descriptionFirst.group(2),
                    descriptionFirst.group(3),
                    descriptionFirst.group(1)
            );
        }
        return Optional.empty();
    }

    private static Optional<QuickExpense> parsed(String rawText,
                                                 String amountText,
                                                 String multiplier,
                                                 String descriptionText) {
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountText
                    .replace(" ", "")
                    .replace("\u00A0", "")
                    .replace(',', '.'));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
        if (multiplier != null && !multiplier.isBlank()) {
            String normalizedMultiplier = multiplier.toLowerCase(Locale.ROOT);
            if (normalizedMultiplier.startsWith("к")
                    || normalizedMultiplier.startsWith("k")
                    || normalizedMultiplier.startsWith("тыс")) {
                amount = amount.multiply(BigDecimal.valueOf(1_000));
            }
        }

        String description = descriptionText.trim();
        if (amount.signum() <= 0 || description.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new QuickExpense(rawText.trim(), amount, description));
    }
}
