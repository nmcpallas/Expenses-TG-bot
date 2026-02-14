package com.cpallas.expenses;

import com.cpallas.expenses.storage.ids.CategoryId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserSession {

    private Step step;
    private String description;
    private BigDecimal amount;
    private CategoryId categoryId;
}
