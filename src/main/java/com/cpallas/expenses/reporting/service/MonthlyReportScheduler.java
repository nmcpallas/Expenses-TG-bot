package com.cpallas.expenses.reporting.service;

import com.cpallas.expenses.observability.TraceContext;
import com.cpallas.expenses.reporting.contract.MonthlyReportRequested;
import com.cpallas.expenses.reporting.contract.ReportPeriod;
import com.cpallas.expenses.storage.jpa.ChatJpa;
import com.cpallas.expenses.storage.jpa.MonthlyReportJobJpa;
import com.cpallas.expenses.storage.repo.ChatRepo;
import com.cpallas.expenses.storage.repo.MonthlyReportJobRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyReportScheduler {

    private final ChatRepo chatRepo;
    private final MonthlyReportJobRepo monthlyReportJobRepo;
    private final AnalyticsEventPublisher publisher;

    @Value("${expense.reporting.enabled}")
    private boolean enabled;

    @Value("${expense.reporting.zone}")
    private String zone;

    @Scheduled(cron = "${expense.reporting.cron}", zone = "${expense.reporting.zone}")
    @Transactional
    public void scheduleMonthlyReports() {
        if (!enabled) {
            return;
        }
        try (TraceContext.TraceScope ignored = TraceContext.open()) {
            scheduleForDate(LocalDate.now(ZoneId.of(zone)));
        }
    }

    @Transactional
    public void scheduleForDate(LocalDate today) {
        int published = 0;
        for (ChatJpa chat : chatRepo.findAll()) {
            if (chat.getMonthStart() == null || chat.getMonthStart() != today.getDayOfMonth()) {
                continue;
            }
            LocalDate periodStart = today.minusMonths(1);
            if (monthlyReportJobRepo.findByChatIdAndPeriodStartAndPeriodEnd(chat.getId().getId(), periodStart, today).isPresent()) {
                continue;
            }

            UUID eventId = UUID.nameUUIDFromBytes(
                    ("monthly-report:" + chat.getId().getId() + ":" + periodStart + ":" + today).getBytes(StandardCharsets.UTF_8)
            );
            MonthlyReportJobJpa job = new MonthlyReportJobJpa();
            job.setId(UUID.randomUUID());
            job.setChatId(chat.getId().getId());
            job.setPeriodStart(periodStart);
            job.setPeriodEnd(today);
            job.setEventId(eventId);
            job.setStatus("REQUESTED");
            job.setCreatedAt(ZonedDateTime.now(ZoneId.of(zone)));
            monthlyReportJobRepo.saveAndFlush(job);

            publisher.publish(MonthlyReportRequested.of(
                    eventId,
                    chat.getId().getId(),
                    new ReportPeriod(periodStart, today),
                    new ReportPeriod(periodStart.minusMonths(1), periodStart),
                    zone
            ));
            published++;
        }
        log.info("Monthly report scheduler finished: date={}, published={}", today, published);
    }
}
