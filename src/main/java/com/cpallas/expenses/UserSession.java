package com.cpallas.expenses;

import com.cpallas.expenses.storage.ids.CategoryId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserSession {

    private Step step;
    private String description;
    private Double amount;
    private CategoryId categoryId;
}
