package com.cpallas.expenses.reporting.contract;

import java.time.LocalDate;

public record ReportPeriod(LocalDate start, LocalDate end) {
}
