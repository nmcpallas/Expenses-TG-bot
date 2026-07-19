package com.cpallas.expenses.service.ml;

import com.cpallas.expenses.service.ExpenseService;
import com.cpallas.expenses.storage.ids.ChatId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuickExpenseEligibilityServiceTest {

    private static final ChatId CHAT_ID = new ChatId(1L);

    @Mock
    private ExpenseService expenseService;

    private QuickExpenseEligibilityService service;

    @BeforeEach
    void setUp() {
        service = new QuickExpenseEligibilityService(expenseService);
    }

    @Test
    void disablesQuickFlowWhenThereAreTooFewCategories() {
        when(expenseService.countExpenses(CHAT_ID)).thenReturn(30L);

        QuickExpenseMode mode = service.resolveMode(CHAT_ID, 1);

        assertThat(mode).isEqualTo(QuickExpenseMode.DISABLED);
    }

    @Test
    void disablesQuickFlowWhenThereAreTooFewExpenses() {
        when(expenseService.countExpenses(CHAT_ID)).thenReturn(9L);

        QuickExpenseMode mode = service.resolveMode(CHAT_ID, 3);

        assertThat(mode).isEqualTo(QuickExpenseMode.DISABLED);
    }

    @Test
    void enablesReviewOnlyWhenDataIsEnoughForReviewButNotAutoSave() {
        when(expenseService.countExpenses(CHAT_ID)).thenReturn(10L);

        QuickExpenseMode mode = service.resolveMode(CHAT_ID, 2);

        assertThat(mode).isEqualTo(QuickExpenseMode.REVIEW_ONLY);
    }

    @Test
    void enablesAutoSaveWhenThereIsEnoughData() {
        when(expenseService.countExpenses(CHAT_ID)).thenReturn(30L);

        QuickExpenseMode mode = service.resolveMode(CHAT_ID, 3);

        assertThat(mode).isEqualTo(QuickExpenseMode.AUTO_SAVE_ALLOWED);
    }
}
