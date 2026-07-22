package com.cpallas.expenses.reporting.service;

import com.cpallas.expenses.reporting.config.ReportingRabbitConfig;
import com.cpallas.expenses.reporting.contract.MonthlyReportReady;
import com.cpallas.expenses.storage.jpa.MonthlyReportJobJpa;
import com.cpallas.expenses.storage.repo.MonthlyReportJobRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.ZonedDateTime;

import static com.cpallas.expenses.controller.util.MessageUtil.createMessage;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyReportReadyListener {

    private final MonthlyReportJobRepo monthlyReportJobRepo;
    private final MonthlyReportMessageFormatter formatter;
    private final TelegramClient telegramClient;

    @Transactional
    @RabbitListener(queues = ReportingRabbitConfig.REPORT_READY_QUEUE)
    public void receive(MonthlyReportReady event) throws Exception {
        MonthlyReportJobJpa job = monthlyReportJobRepo.findByChatIdAndPeriodStartAndPeriodEnd(
                        event.chatId(), event.period().start(), event.period().end())
                .orElseThrow(() -> new IllegalStateException("Monthly report job was not found for report " + event.reportId()));
        if ("DELIVERED".equals(job.getStatus())) {
            log.info("Skipping already delivered monthly report: reportId={}", event.reportId());
            return;
        }

        telegramClient.execute(createMessage(formatter.format(event), event.chatId()));
        job.setReportId(event.reportId());
        job.setStatus("DELIVERED");
        job.setDeliveredAt(ZonedDateTime.now());
        log.info("Monthly report delivered: reportId={}, chatId={}", event.reportId(), event.chatId());
    }
}
