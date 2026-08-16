package com.cpallas.expenses.miniapp;

import com.cpallas.expenses.service.ExpenseService;
import com.cpallas.expenses.storage.ids.CategoryId;
import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.ids.UserId;
import com.cpallas.expenses.storage.jpa.CategoryJpa;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MiniAppServiceTest {

    @Test
    void updatesCategorySpendingGoal() {
        ExpenseService expenseService = mock(ExpenseService.class);
        MiniAppService service = new MiniAppService(
                expenseService,
                mock(AnalyticsMiniAppClient.class)
        );
        TelegramMiniAppPrincipal principal = new TelegramMiniAppPrincipal(
                new UserId(42L),
                new ChatId(-100123L),
                "Наиль",
                "tester"
        );
        CategoryId categoryId = new CategoryId(UUID.randomUUID());
        BigDecimal spendingLimit = new BigDecimal("250000");
        CategoryJpa category = new CategoryJpa();
        category.setId(categoryId);
        category.setName("Продукты");
        category.setSpendingLimit(spendingLimit);
        when(expenseService.updateCategoryLimit(
                same(principal.chatId()),
                same(principal.userId()),
                same(categoryId),
                same(spendingLimit)
        )).thenReturn(category);

        MiniAppDtos.Category updated = service.updateCategory(
                principal,
                categoryId,
                new MiniAppDtos.UpdateCategory(spendingLimit)
        );

        assertThat(updated.id()).isEqualTo(categoryId.getId());
        assertThat(updated.name()).isEqualTo("Продукты");
        assertThat(updated.spendingLimit()).isEqualByComparingTo(spendingLimit);
        verify(expenseService).updateCategoryLimit(
                same(principal.chatId()),
                same(principal.userId()),
                same(categoryId),
                same(spendingLimit)
        );
    }

    @Test
    void rejectsMissingMutationBodiesWithClientErrors() {
        MiniAppService service = new MiniAppService(
                mock(ExpenseService.class),
                mock(AnalyticsMiniAppClient.class)
        );
        TelegramMiniAppPrincipal principal = new TelegramMiniAppPrincipal(
                new UserId(42L),
                new ChatId(42L),
                "Наиль",
                "tester"
        );

        assertThatThrownBy(() -> service.updateCategory(
                principal, new CategoryId(UUID.randomUUID()), null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("body is required");
        assertThatThrownBy(() -> service.updateExpense(
                principal,
                new com.cpallas.expenses.storage.ids.ExpenseId(UUID.randomUUID()),
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("body is required");
        assertThatThrownBy(() -> service.updateSettings(principal, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("body is required");
    }
}
