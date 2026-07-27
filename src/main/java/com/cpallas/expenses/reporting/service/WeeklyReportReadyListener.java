package com.cpallas.expenses.reporting.service;

import com.cpallas.expenses.reporting.config.ReportingRabbitConfig;
import com.cpallas.expenses.reporting.contract.WeeklyReportReady;
import com.cpallas.expenses.storage.repo.WeeklyReportDeliveryRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyReportReadyListener {

    private final WeeklyReportDeliveryRepo deliveryRepo;
    private final WeeklyReportMessageFormatter formatter;
    private final TelegramClient telegramClient;
    private final ReportArtifactDownloader artifactDownloader;

    @Value("${expense.weekly-report.zone:${expense.reporting.zone}}")
    private String zone;

    @RabbitListener(queues = ReportingRabbitConfig.WEEKLY_READY_QUEUE)
    public void receive(WeeklyReportReady event) throws Exception {
        int claimed = deliveryRepo.claim(
                event.chatId(),
                event.period().start(),
                event.period().end()
        );
        if (claimed == 0) {
            log.info("Skipping duplicate weekly report: reportId={}", event.reportId());
            return;
        }

        try {
            if (event.report().expensesCount() > 0) {
                byte[] excel = artifactDownloader.download(event.artifact());
                telegramClient.execute(SendDocument.builder()
                        .chatId(event.chatId())
                        .document(new InputFile(
                                new ByteArrayInputStream(excel),
                                event.artifact().fileName()
                        ))
                        .caption(caption(formatter.format(event.report())))
                        .build());
            }
            deliveryRepo.markDelivered(
                    event.chatId(),
                    event.period().start(),
                    event.period().end(),
                    event.reportId(),
                    ZonedDateTime.now(ZoneId.of(zone))
            );
            log.info("Weekly report delivered: reportId={}, chatId={}", event.reportId(), event.chatId());
        } catch (Exception exception) {
            deliveryRepo.release(
                    event.chatId(),
                    event.period().start(),
                    event.period().end()
            );
            throw exception;
        }
    }

    private String caption(String report) {
        int telegramCaptionLimit = 1_024;
        if (report.length() <= telegramCaptionLimit) {
            return report;
        }
        return report.substring(0, telegramCaptionLimit - 1) + "…";
    }
}
