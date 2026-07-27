package com.cpallas.expenses.reporting.service;

import com.cpallas.expenses.reporting.config.ReportingRabbitConfig;
import com.cpallas.expenses.reporting.contract.MonthlyReportReady;
import com.cpallas.expenses.storage.repo.MonthlyReportJobRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.objects.InputFile;

import java.io.ByteArrayInputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyReportReadyListener {

    private final MonthlyReportJobRepo monthlyReportJobRepo;
    private final MonthlyReportMessageFormatter formatter;
    private final TelegramClient telegramClient;
    private final ReportArtifactDownloader artifactDownloader;

    @Value("${expense.reporting.zone}")
    private String zone;

    @RabbitListener(queues = ReportingRabbitConfig.MONTHLY_READY_QUEUE)
    public void receive(MonthlyReportReady event) throws Exception {
        int claimed = monthlyReportJobRepo.claim(
                event.chatId(),
                event.period().start(),
                event.period().end()
        );
        if (claimed == 0) {
            var job = monthlyReportJobRepo.findByChatIdAndPeriodStartAndPeriodEnd(
                            event.chatId(),
                            event.period().start(),
                            event.period().end()
                    )
                    .orElseThrow(() -> new IllegalStateException(
                            "Monthly report job was not found for report " + event.reportId()
                    ));
            log.info(
                    "Skipping monthly report in status {}: reportId={}",
                    job.getStatus(),
                    event.reportId()
            );
            return;
        }

        try {
            byte[] excel = artifactDownloader.download(event.artifact());
            telegramClient.execute(SendDocument.builder()
                    .chatId(event.chatId())
                    .document(new InputFile(
                            new ByteArrayInputStream(excel),
                            event.artifact().fileName()
                    ))
                    .caption(caption(formatter.format(event)))
                    .build());
            monthlyReportJobRepo.markDelivered(
                    event.chatId(),
                    event.period().start(),
                    event.period().end(),
                    event.reportId(),
                    ZonedDateTime.now(ZoneId.of(zone))
            );
            log.info("Monthly report delivered: reportId={}, chatId={}", event.reportId(), event.chatId());
        } catch (Exception exception) {
            monthlyReportJobRepo.release(
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
