package com.cpallas.expenses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserSession {

    private Step step = Step.NONE;
    private String description;
    private Integer amount;
}
