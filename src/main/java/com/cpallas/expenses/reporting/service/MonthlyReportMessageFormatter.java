package com.cpallas.expenses.reporting.service;

import com.cpallas.expenses.reporting.contract.CategoryReport;
import com.cpallas.expenses.reporting.contract.MonthlyReportReady;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class MonthlyReportMessageFormatter {

    private static final Locale RUSSIAN = Locale.forLanguageTag("ru-RU");
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("d MMMM", RUSSIAN);

    public String format(MonthlyReportReady event) {
        var report = event.report();
        StringBuilder message = new StringBuilder("📊 Месячный отчёт за ")
                .append(event.period().start().format(PERIOD_FORMAT))
                .append(" — ")
                .append(event.period().end().minusDays(1).format(PERIOD_FORMAT))
                .append("\n\nВсего потрачено: ").append(money(report.totalAmount()));

        if (report.previousTotalAmount() != null) {
            message.append("\nПредыдущий период: ").append(money(report.previousTotalAmount()));
            if (report.differenceAmount() != null) {
                message.append("\nРазница: ").append(signedMoney(report.differenceAmount()));
                if (report.differencePercent() != null) {
                    message.append(" (").append(report.differencePercent().stripTrailingZeros().toPlainString()).append("%)");
                }
            }
        }
        if (report.monthLimit() != null) {
            message.append("\nЛимит: ").append(money(report.monthLimit()));
            if (report.limitUsagePercent() != null) {
                message.append(" (использовано ").append(report.limitUsagePercent().stripTrailingZeros().toPlainString()).append("%)");
            }
        }
        if (report.categories() != null && !report.categories().isEmpty()) {
            message.append("\n\nТоп категорий:");
            for (CategoryReport category : report.categories()) {
                message.append("\n• ").append(category.name()).append(": ").append(money(category.amount()));
            }
        }
        if (report.insights() != null && !report.insights().isEmpty()) {
            message.append("\n\n💡 ").append(String.join("\n• ", report.insights()));
        }
        return message.toString();
    }

    private String money(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private String signedMoney(BigDecimal value) {
        return (value.signum() > 0 ? "+" : "") + money(value);
    }
}
