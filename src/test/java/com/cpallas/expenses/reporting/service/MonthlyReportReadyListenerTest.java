package com.cpallas.expenses.reporting.service;

import com.cpallas.expenses.reporting.contract.MonthlyReport;
import com.cpallas.expenses.reporting.contract.MonthlyReportReady;
import com.cpallas.expenses.reporting.contract.ReportArtifact;
import com.cpallas.expenses.reporting.contract.ReportPeriod;
import com.cpallas.expenses.storage.jpa.MonthlyReportJobJpa;
import com.cpallas.expenses.storage.repo.MonthlyReportJobRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class MonthlyReportReadyListenerTest {

    @Mock
    private MonthlyReportJobRepo monthlyReportJobRepo;
    @Mock
    private TelegramClient telegramClient;
    @Mock
    private ReportArtifactDownloader artifactDownloader;

    private MonthlyReportReadyListener listener;

    @BeforeEach
    void setUp() {
        listener = new MonthlyReportReadyListener(
                monthlyReportJobRepo,
                new MonthlyReportMessageFormatter(),
                telegramClient,
                artifactDownloader
        );
        ReflectionTestUtils.setField(listener, "zone", "Asia/Tashkent");
    }

    @Test
    void sendsReportAndMarksJobDelivered() throws Exception {
        MonthlyReportReady event = event();
        when(monthlyReportJobRepo.claim(42L, event.period().start(), event.period().end()))
                .thenReturn(1);
        when(artifactDownloader.download(event.artifact())).thenReturn(new byte[]{1, 2, 3});

        listener.receive(event);

        verify(telegramClient, times(1)).execute(any(SendDocument.class));
        verify(monthlyReportJobRepo).markDelivered(
                eq(42L),
                eq(event.period().start()),
                eq(event.period().end()),
                eq(event.reportId()),
                any()
        );
    }

    @Test
    void skipsAlreadyDeliveredReport() throws Exception {
        MonthlyReportReady event = event();
        MonthlyReportJobJpa job = new MonthlyReportJobJpa();
        job.setStatus("DELIVERED");
        when(monthlyReportJobRepo.claim(42L, event.period().start(), event.period().end()))
                .thenReturn(0);
        when(monthlyReportJobRepo.findByChatIdAndPeriodStartAndPeriodEnd(42L, event.period().start(), event.period().end()))
                .thenReturn(Optional.of(job));

        listener.receive(event);

        verify(telegramClient, never()).execute(any(SendDocument.class));
    }

    @Test
    void releasesClaimWhenTelegramDeliveryFails() throws Exception {
        MonthlyReportReady event = event();
        when(monthlyReportJobRepo.claim(42L, event.period().start(), event.period().end()))
                .thenReturn(1);
        when(artifactDownloader.download(event.artifact())).thenReturn(new byte[]{1, 2, 3});
        when(telegramClient.execute(any(SendDocument.class)))
                .thenThrow(new TelegramApiException("Telegram unavailable"));

        assertThatThrownBy(() -> listener.receive(event))
                .isInstanceOf(TelegramApiException.class);

        verify(monthlyReportJobRepo).release(
                42L,
                event.period().start(),
                event.period().end()
        );
        verify(monthlyReportJobRepo, never()).markDelivered(
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    private MonthlyReportReady event() {
        return new MonthlyReportReady(
                UUID.randomUUID(), "MonthlyReportReady", 1, UUID.randomUUID(), 42L,
                new ReportPeriod(LocalDate.of(2026, 6, 22), LocalDate.of(2026, 7, 22)),
                new MonthlyReport(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, null, null, null, List.of(), List.of()),
                new ReportArtifact(
                        "expense-reports",
                        "monthly/42/report.xlsx",
                        "report.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        3,
                        "checksum",
                        "http://localhost:9000/report"
                ),
                UUID.randomUUID(), ZonedDateTime.now()
        );
    }
}
