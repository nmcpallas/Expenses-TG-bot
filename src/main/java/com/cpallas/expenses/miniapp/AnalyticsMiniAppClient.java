package com.cpallas.expenses.miniapp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class AnalyticsMiniAppClient {

    private final RestClient restClient;

    public AnalyticsMiniAppClient(
            RestClient.Builder builder,
            @Value("${expense.analytics.base-url:http://expense-analytics:8080}") String baseUrl
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public Optional<DashboardAnalytics> dashboard(
            long chatId,
            LocalDate start,
            LocalDate end,
            String timezone
    ) {
        return dashboard(chatId, start, end, start.minusMonths(1), start, timezone);
    }

    public Optional<DashboardAnalytics> dashboard(
            long chatId,
            LocalDate start,
            LocalDate end,
            LocalDate comparisonStart,
            LocalDate comparisonEnd,
            String timezone
    ) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/analytics/dashboard")
                            .queryParam("chatId", chatId)
                            .queryParam("start", start)
                            .queryParam("end", end)
                            .queryParam("comparisonStart", comparisonStart)
                            .queryParam("comparisonEnd", comparisonEnd)
                            .queryParam("timezone", timezone)
                            .build())
                    .retrieve()
                    .body(DashboardAnalytics.class));
        } catch (RuntimeException exception) {
            log.warn("Interactive analytics is unavailable: chatId={}", chatId);
            return Optional.empty();
        }
    }

    public record DashboardAnalytics(
            Period period,
            Report report,
            List<MiniAppDtos.DailySpending> dailySpending
    ) {
        public DashboardAnalytics {
            dailySpending = dailySpending == null ? List.of() : List.copyOf(dailySpending);
        }
    }

    public record Period(LocalDate start, LocalDate end) {
    }

    public record Report(
            BigDecimal totalAmount,
            BigDecimal previousTotalAmount,
            List<Category> categories
    ) {
        public Report {
            categories = categories == null ? List.of() : List.copyOf(categories);
        }
    }

    public record Category(String name, BigDecimal amount) {
    }
}
