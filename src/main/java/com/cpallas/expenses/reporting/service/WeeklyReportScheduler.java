package com.cpallas.expenses.reporting.service;

import com.cpallas.expenses.reporting.contract.ReportPeriod;
import com.cpallas.expenses.reporting.contract.WeeklyReportRequested;
import com.cpallas.expenses.storage.jpa.WeeklyReportDeliveryJpa;
import com.cpallas.expenses.storage.repo.ChatRepo;
import com.cpallas.expenses.storage.repo.WeeklyReportDeliveryRepo;
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
public class WeeklyReportScheduler {

    private final ChatRepo chatRepo;
    private final WeeklyReportDeliveryRepo deliveryRepo;
    private final AnalyticsEventPublisher publisher;

    @Value("${expense.weekly-report.enabled:true}")
    private boolean enabled;

    @Value("${expense.weekly-report.zone:${expense.reporting.zone}}")
    private String zone;

    @Scheduled(
            cron = "${expense.weekly-report.cron:0 0 10 * * MON}",
            zone = "${expense.weekly-report.zone:${expense.reporting.zone}}"
    )
    @Transactional
    public void scheduleWeeklyReports() {
        if (!enabled) {
            return;
        }
        sendForDate(LocalDate.now(ZoneId.of(zone)));
    }

    @Transactional
    public void sendForDate(LocalDate periodEnd) {
        LocalDate periodStart = periodEnd.minusDays(7);
        ZoneId reportZone = ZoneId.of(zone);
        int published = 0;
        for (var chat : chatRepo.findAll()) {
            if (!chat.isWeeklyReportEnabled()) {
                continue;
            }
            Long chatId = chat.getId().getId();
            if (deliveryRepo.findByChatIdAndPeriodStartAndPeriodEnd(chatId, periodStart, periodEnd).isPresent()) {
                continue;
            }

            UUID eventId = UUID.nameUUIDFromBytes(
                    ("weekly-report:" + chatId + ":" + periodStart + ":" + periodEnd)
                            .getBytes(StandardCharsets.UTF_8)
            );
            WeeklyReportDeliveryJpa delivery = new WeeklyReportDeliveryJpa();
            delivery.setId(UUID.randomUUID());
            delivery.setEventId(eventId);
            delivery.setChatId(chatId);
            delivery.setPeriodStart(periodStart);
            delivery.setPeriodEnd(periodEnd);
            delivery.setStatus("REQUESTED");
            delivery.setRequestedAt(ZonedDateTime.now(reportZone));
            deliveryRepo.save(delivery);

            publisher.publish(WeeklyReportRequested.of(
                    eventId,
                    chatId,
                    new ReportPeriod(periodStart, periodEnd),
                    new ReportPeriod(periodStart.minusDays(7), periodStart),
                    zone
            ));
            published++;
        }
        log.info("Weekly report scheduler finished: period={}..{}, published={}",
                periodStart, periodEnd, published);
    }
}
