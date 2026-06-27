package com.cpallas.expenses.config.ml;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "expense.ml")
public record ExpenseMlProperties(
        boolean enabled,
        String host,
        int port,
        long deadlineMs
) {
}
