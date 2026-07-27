package com.cpallas.expenses.service;

import com.cpallas.expenses.controller.dto.SpendingStatus;
import com.cpallas.expenses.storage.jpa.CategoryJpa;
import com.cpallas.expenses.storage.jpa.ExpenseJpa;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseMessageFormatterTest {

    @Test
    void showsCategoryAndOverallLimitsAfterSave() {
        CategoryJpa category = new CategoryJpa();
        category.setName("Кафе");
        ExpenseJpa expense = new ExpenseJpa();
        expense.setAmount(new BigDecimal("150"));
        expense.setDescription("обед");
        expense.setCategory(category);
        SpendingStatus status = new SpendingStatus(
                new BigDecimal("1000"),
                new BigDecimal("600"),
                Map.of("Кафе", new BigDecimal("300")),
                Map.of("Кафе", new BigDecimal("500"))
        );

        String message = new ExpenseMessageFormatter().saved(expense, status);

        assertThat(message)
                .contains("В категории «Кафе» за текущий период: 300 из 500")
                .contains("Остаток по категории: 200")
                .contains("Всего потрачено: 600 из 1000")
                .contains("Остаток по плану: 400");
    }
}
