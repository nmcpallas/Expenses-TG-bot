package com.cpallas.expenses.service.ml;

import com.cpallas.expenses.service.dto.QuickExpense;
import com.cpallas.expenses.service.util.QuickExpenseParser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class QuickExpenseParserTest {

    @Test
    void parseAmountAndDescription() {
        QuickExpense expense = QuickExpenseParser.parse("250 кофе").orElseThrow();

        assertThat(expense.rawText()).isEqualTo("250 кофе");
        assertThat(expense.amount()).isEqualByComparingTo(new BigDecimal("250"));
        assertThat(expense.description()).isEqualTo("кофе");
    }

    @Test
    void parseCommaDecimalAmount() {
        QuickExpense expense = QuickExpenseParser.parse("250,50 кофе").orElseThrow();

        assertThat(expense.amount()).isEqualByComparingTo(new BigDecimal("250.50"));
        assertThat(expense.description()).isEqualTo("кофе");
    }

    @Test
    void parsesDescriptionBeforeAmount() {
        assertThat(QuickExpenseParser.parse("кофе 250"))
                .contains(new QuickExpense("кофе 250", new BigDecimal("250"), "кофе"));
    }

    @Test
    void parsesThousandsSuffixAndSpaces() {
        assertThat(QuickExpenseParser.parse("кофе 35к"))
                .contains(new QuickExpense("кофе 35к", new BigDecimal("35000"), "кофе"));
        assertThat(QuickExpenseParser.parse("120 000 продукты"))
                .contains(new QuickExpense("120 000 продукты", new BigDecimal("120000"), "продукты"));
    }

    @Test
    void keepsNumbersInDescriptionSeparateFromFinalAmount() {
        assertThat(QuickExpenseParser.parse("айфон 15 100000"))
                .contains(new QuickExpense(
                        "айфон 15 100000",
                        new BigDecimal("100000"),
                        "айфон 15"
                ));
    }
}
