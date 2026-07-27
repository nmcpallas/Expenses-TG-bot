package com.cpallas.expenses.reporting.service;

import com.cpallas.expenses.reporting.contract.WeeklyReport;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class WeeklyReportMessageFormatter {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru-RU"));

    public String format(WeeklyReport report) {
        StringBuilder message = new StringBuilder("📊 Итоги недели ")
                .append(report.periodStart().format(DATE_FORMAT))
                .append(" — ")
                .append(report.periodEnd().minusDays(1).format(DATE_FORMAT))
                .append("\n\nПотрачено: ")
                .append(money(report.totalAmount()))
                .append("\nОпераций: ")
                .append(report.expensesCount());

        if (report.previousTotalAmount().signum() > 0) {
            message.append("\nК прошлой неделе: ")
                    .append(report.differenceAmount().signum() > 0 ? "+" : "")
                    .append(money(report.differenceAmount()));
            if (report.differencePercent() != null) {
                message.append(" (")
                        .append(report.differencePercent().signum() > 0 ? "+" : "")
                        .append(report.differencePercent().stripTrailingZeros().toPlainString())
                        .append("%)");
            }
        }

        if (!report.categories().isEmpty()) {
            message.append("\n\nБольше всего:");
            report.categories().entrySet().stream()
                    .limit(3)
                    .forEach(entry -> message.append("\n• ")
                            .append(entry.getKey())
                            .append(": ")
                            .append(money(entry.getValue())));
        }
        if (report.unusualCategory() != null) {
            message.append("\n\n⚡ Заметно выросла категория «")
                    .append(report.unusualCategory())
                    .append("». Проверьте, всё ли идёт по плану.");
        }
        return message.toString();
    }

    private String money(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
