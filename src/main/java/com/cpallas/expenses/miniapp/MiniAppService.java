package com.cpallas.expenses.miniapp;

import com.cpallas.expenses.controller.dto.SpendingStatus;
import com.cpallas.expenses.service.ExpenseService;
import com.cpallas.expenses.storage.ids.CategoryId;
import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.ids.ExpenseId;
import com.cpallas.expenses.storage.jpa.CategoryJpa;
import com.cpallas.expenses.storage.jpa.ExpenseJpa;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MiniAppService {

    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Tashkent");
    private final ExpenseService expenseService;
    private final AnalyticsMiniAppClient analyticsClient;

    @Transactional
    public MiniAppDtos.Dashboard dashboard(TelegramMiniAppPrincipal principal) {
        ChatId chatId = principal.chatId();
        SpendingStatus status = expenseService.getStatus(chatId, principal.userId());
        var settings = expenseService.getBudgetSettings(chatId, principal.userId());
        LocalDate today = LocalDate.now(REPORT_ZONE);
        LocalDate start = currentPeriodStart(today, settings.getMonthStart());
        LocalDate end = start.plusMonths(1);
        var analytics = analyticsClient.dashboard(
                chatId.getId(),
                start,
                end,
                REPORT_ZONE.getId()
        );
        BigDecimal limit = zeroIfNull(status.monthLimit());
        BigDecimal spent = zeroIfNull(status.spent());
        BigDecimal remaining = limit.signum() > 0
                ? limit.subtract(spent).max(BigDecimal.ZERO)
                : BigDecimal.ZERO;
        BigDecimal usage = limit.signum() > 0
                ? spent.multiply(BigDecimal.valueOf(100)).divide(limit, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal previousSpent = analytics
                .map(AnalyticsMiniAppClient.DashboardAnalytics::report)
                .map(AnalyticsMiniAppClient.Report::previousTotalAmount)
                .orElse(BigDecimal.ZERO);
        List<MiniAppDtos.CategorySummary> categories = status.spendingByCategories().entrySet().stream()
                .sorted(MapEntryByAmount.INSTANCE)
                .map(entry -> new MiniAppDtos.CategorySummary(
                        entry.getKey(),
                        entry.getValue(),
                        spent.signum() == 0
                                ? BigDecimal.ZERO
                                : entry.getValue().multiply(BigDecimal.valueOf(100))
                                        .divide(spent, 1, RoundingMode.HALF_UP)
                ))
                .toList();
        return new MiniAppDtos.Dashboard(
                principal.firstName(),
                start,
                end,
                spent,
                previousSpent,
                limit,
                remaining,
                usage,
                categories,
                analytics.map(AnalyticsMiniAppClient.DashboardAnalytics::dailySpending).orElse(List.of()),
                expenses(principal, 5)
        );
    }

    @Transactional
    public List<MiniAppDtos.Expense> expenses(TelegramMiniAppPrincipal principal, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return expenseService.getRecentExpenses(
                        principal.chatId(),
                        principal.userId(),
                        safeLimit
                ).stream()
                .map(this::expense)
                .toList();
    }

    @Transactional
    public List<MiniAppDtos.Category> categories(TelegramMiniAppPrincipal principal) {
        return expenseService.getOrCreateCategories(
                        principal.chatId(),
                        principal.userId()
                ).stream()
                .sorted(Comparator.comparing(CategoryJpa::getName, String.CASE_INSENSITIVE_ORDER))
                .map(category -> new MiniAppDtos.Category(
                        category.getId().getId(),
                        category.getName(),
                        category.getSpendingLimit()
                ))
                .toList();
    }

    @Transactional
    public MiniAppDtos.Category updateCategory(
            TelegramMiniAppPrincipal principal,
            CategoryId categoryId,
            MiniAppDtos.UpdateCategory request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Category update body is required.");
        }
        CategoryJpa category = expenseService.updateCategoryLimit(
                principal.chatId(),
                principal.userId(),
                categoryId,
                request.spendingLimit()
        );
        return new MiniAppDtos.Category(
                category.getId().getId(),
                category.getName(),
                category.getSpendingLimit()
        );
    }

    @Transactional
    public MiniAppDtos.Settings deleteCategory(
            TelegramMiniAppPrincipal principal,
            CategoryId categoryId
    ) {
        var chat = expenseService.deleteCategory(
                principal.chatId(),
                principal.userId(),
                categoryId
        );
        return settings(chat);
    }

    @Transactional
    public MiniAppDtos.Analytics analytics(TelegramMiniAppPrincipal principal, String period) {
        LocalDate today = LocalDate.now(REPORT_ZONE);
        LocalDate start;
        LocalDate end;
        switch (period) {
            case "week" -> {
                start = today.minusDays(6);
                end = today.plusDays(1);
            }
            case "half" -> {
                start = today.withDayOfMonth(1).minusMonths(5);
                end = today.withDayOfMonth(1).plusMonths(1);
            }
            case "month" -> {
                var settings = expenseService.getBudgetSettings(
                        principal.chatId(), principal.userId()
                );
                start = currentPeriodStart(today, settings.getMonthStart());
                end = start.plusMonths(1);
            }
            default -> throw new IllegalArgumentException("Unknown analytics period.");
        }
        long periodDays = ChronoUnit.DAYS.between(start, end);
        var analytics = analyticsClient.dashboard(
                principal.chatId().getId(),
                start,
                end,
                start.minusDays(periodDays),
                start,
                REPORT_ZONE.getId()
        ).orElse(null);
        if (analytics == null || analytics.report() == null) {
            return new MiniAppDtos.Analytics(
                    start, end, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of()
            );
        }
        BigDecimal total = zeroIfNull(analytics.report().totalAmount());
        List<MiniAppDtos.CategorySummary> categories = analytics.report().categories().stream()
                .map(category -> new MiniAppDtos.CategorySummary(
                        category.name(),
                        zeroIfNull(category.amount()),
                        total.signum() == 0
                                ? BigDecimal.ZERO
                                : zeroIfNull(category.amount()).multiply(BigDecimal.valueOf(100))
                                        .divide(total, 1, RoundingMode.HALF_UP)
                ))
                .toList();
        return new MiniAppDtos.Analytics(
                start,
                end,
                total,
                zeroIfNull(analytics.report().previousTotalAmount()),
                categories,
                analytics.dailySpending()
        );
    }

    @Transactional
    public MiniAppDtos.Expense updateExpense(
            TelegramMiniAppPrincipal principal,
            ExpenseId expenseId,
            MiniAppDtos.UpdateExpense request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Expense update body is required.");
        }
        ChatId chatId = principal.chatId();
        ExpenseJpa expense = null;
        if (request.amount() != null) {
            expense = expenseService.updateExpenseAmount(
                    chatId, principal.userId(), expenseId, request.amount()
            );
        }
        if (request.description() != null) {
            expense = expenseService.updateExpenseDescription(
                    chatId, principal.userId(), expenseId, request.description()
            );
        }
        if (request.categoryId() != null) {
            expense = expenseService.updateExpenseCategory(
                    chatId,
                    principal.userId(),
                    expenseId,
                    new CategoryId(request.categoryId())
            );
        }
        if (expense == null) {
            throw new IllegalArgumentException("At least one expense field is required.");
        }
        return expense(expense);
    }

    @Transactional
    public void deleteExpense(TelegramMiniAppPrincipal principal, ExpenseId expenseId) {
        var deleted = expenseService.deleteExpense(
                principal.chatId(),
                principal.userId(),
                expenseId
        );
        if (deleted.isEmpty()) {
            throw new IllegalArgumentException("Expense was not found.");
        }
    }

    @Transactional
    public MiniAppDtos.Settings settings(TelegramMiniAppPrincipal principal) {
        var chat = expenseService.getBudgetSettings(
                principal.chatId(),
                principal.userId()
        );
        return settings(chat);
    }

    @Transactional
    public MiniAppDtos.Settings updateSettings(
            TelegramMiniAppPrincipal principal,
            MiniAppDtos.UpdateSettings request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Settings update body is required.");
        }
        var chat = expenseService.updateBudgetSettings(
                principal.chatId(),
                principal.userId(),
                request.monthStart(),
                request.weeklyReportEnabled(),
                request.unusualNotificationsEnabled()
        );
        return settings(chat);
    }

    private MiniAppDtos.Settings settings(com.cpallas.expenses.storage.jpa.ChatJpa chat) {
        return new MiniAppDtos.Settings(
                zeroIfNull(chat.getMonthLimit()),
                chat.getMonthStart(),
                chat.isWeeklyReportEnabled(),
                chat.isUnusualNotificationsEnabled()
        );
    }

    private MiniAppDtos.Expense expense(ExpenseJpa expense) {
        return new MiniAppDtos.Expense(
                expense.getId().getId(),
                expense.getAmount(),
                expense.getDescription(),
                expense.getCategory().getId().getId(),
                expense.getCategory().getName(),
                expense.getCreatedAt()
        );
    }

    private LocalDate currentPeriodStart(LocalDate today, int startDay) {
        return today.getDayOfMonth() >= startDay
                ? today.withDayOfMonth(startDay)
                : today.minusMonths(1).withDayOfMonth(startDay);
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private enum MapEntryByAmount implements Comparator<java.util.Map.Entry<String, BigDecimal>> {
        INSTANCE;

        @Override
        public int compare(
                java.util.Map.Entry<String, BigDecimal> left,
                java.util.Map.Entry<String, BigDecimal> right
        ) {
            return right.getValue().compareTo(left.getValue());
        }
    }
}
