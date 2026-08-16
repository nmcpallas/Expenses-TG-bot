package com.cpallas.expenses.miniapp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public final class MiniAppDtos {

    private MiniAppDtos() {
    }

    public record Dashboard(
            String firstName,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal spent,
            BigDecimal previousSpent,
            BigDecimal monthLimit,
            BigDecimal remaining,
            BigDecimal limitUsagePercent,
            List<CategorySummary> categories,
            List<DailySpending> dailySpending,
            List<Expense> recentExpenses
    ) {
    }

    public record CategorySummary(String name, BigDecimal amount, BigDecimal sharePercent) {
    }

    public record DailySpending(LocalDate date, BigDecimal amount) {
    }

    public record Analytics(
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal total,
            BigDecimal previousTotal,
            List<CategorySummary> categories,
            List<DailySpending> dailySpending
    ) {
    }

    public record Expense(
            UUID id,
            BigDecimal amount,
            String description,
            UUID categoryId,
            String categoryName,
            ZonedDateTime createdAt
    ) {
    }

    public record Category(UUID id, String name, BigDecimal spendingLimit) {
    }

    public record UpdateCategory(BigDecimal spendingLimit) {
    }

    public record Settings(
            BigDecimal monthLimit,
            int monthStart,
            boolean weeklyReportEnabled,
            boolean unusualNotificationsEnabled
    ) {
    }

    public record UpdateExpense(
            BigDecimal amount,
            String description,
            UUID categoryId
    ) {
    }

    public record UpdateSettings(
            Integer monthStart,
            Boolean weeklyReportEnabled,
            Boolean unusualNotificationsEnabled
    ) {
    }
}
