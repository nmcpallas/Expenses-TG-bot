package com.cpallas.expenses;

import com.cpallas.expenses.enums.FlowType;
import com.cpallas.expenses.enums.Step;
import com.cpallas.expenses.storage.ids.CategoryId;
import com.cpallas.expenses.storage.ids.ExpenseId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserSession {

    private Step step;
    private FlowType flow;
    private String rawText;
    private String description;
    private BigDecimal amount;
    private CategoryId categoryId;
    private ExpenseId expenseId;

    public UserSession(Step step,
                       FlowType flow,
                       String rawText,
                       String description,
                       BigDecimal amount,
                       CategoryId categoryId) {
        this(step, flow, rawText, description, amount, categoryId, null);
    }
}
