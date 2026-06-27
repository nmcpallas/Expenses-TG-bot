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
    void ignoreTextWithoutLeadingAmount() {
        assertThat(QuickExpenseParser.parse("кофе 250")).isEmpty();
    }
}
