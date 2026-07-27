package com.cpallas.expenses.storage.repo;

import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.ids.ExpenseId;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.EntityGraph;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseRepoTest {

    @Test
    void loadsCategoryWhenFindingLastExpense() throws NoSuchMethodException {
        assertCategoryIsLoaded(
                ExpenseRepo.class.getMethod(
                        "findFirstByChat_IdOrderByCreatedAtDesc",
                        ChatId.class
                )
        );
    }

    @Test
    void loadsCategoryWhenFindingExpenseByIdAndChat() throws NoSuchMethodException {
        assertCategoryIsLoaded(
                ExpenseRepo.class.getMethod(
                        "findByIdAndChat_Id",
                        ExpenseId.class,
                        ChatId.class
                )
        );
    }

    private void assertCategoryIsLoaded(Method repositoryMethod) {
        EntityGraph entityGraph = repositoryMethod.getAnnotation(EntityGraph.class);

        assertThat(entityGraph)
                .as("Expense callbacks format the category after the service transaction closes")
                .isNotNull();
        assertThat(entityGraph.attributePaths()).containsExactly("category");
    }
}
