package com.cpallas.expenses.reporting.service;

import com.cpallas.expenses.reporting.contract.ReportArtifact;
import com.cpallas.expenses.reporting.contract.ReportPeriod;
import com.cpallas.expenses.reporting.contract.WeeklyReport;
import com.cpallas.expenses.reporting.contract.WeeklyReportReady;
import com.cpallas.expenses.storage.repo.WeeklyReportDeliveryRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklyReportReadyListenerTest {

    @Mock
    private WeeklyReportDeliveryRepo deliveryRepo;
    @Mock
    private TelegramClient telegramClient;
    @Mock
    private ReportArtifactDownloader artifactDownloader;

    private WeeklyReportReadyListener listener;

    @BeforeEach
    void setUp() {
        listener = new WeeklyReportReadyListener(
                deliveryRepo,
                new WeeklyReportMessageFormatter(),
                telegramClient,
                artifactDownloader
        );
        ReflectionTestUtils.setField(listener, "zone", "Asia/Tashkent");
    }

    @Test
    void downloadsArtifactSendsItAndMarksDelivery() throws Exception {
        WeeklyReportReady event = event(3);
        when(deliveryRepo.claim(42L, event.period().start(), event.period().end()))
                .thenReturn(1);
        when(artifactDownloader.download(event.artifact())).thenReturn(new byte[]{1, 2, 3});

        listener.receive(event);

        verify(telegramClient).execute(any(SendDocument.class));
        verify(deliveryRepo).markDelivered(
                eq(42L),
                eq(event.period().start()),
                eq(event.period().end()),
                eq(event.reportId()),
                any()
        );
    }

    @Test
    void marksEmptyWeekDeliveredWithoutSendingTelegramDocument() throws Exception {
        WeeklyReportReady event = event(0);
        when(deliveryRepo.claim(42L, event.period().start(), event.period().end()))
                .thenReturn(1);

        listener.receive(event);

        verify(artifactDownloader, never()).download(any());
        verify(telegramClient, never()).execute(any(SendDocument.class));
        verify(deliveryRepo).markDelivered(
                eq(42L),
                eq(event.period().start()),
                eq(event.period().end()),
                eq(event.reportId()),
                any()
        );
    }

    private WeeklyReportReady event(int expensesCount) {
        LocalDate end = LocalDate.of(2026, 7, 27);
        return new WeeklyReportReady(
                UUID.randomUUID(),
                "WeeklyReportReady",
                1,
                UUID.randomUUID(),
                42L,
                new ReportPeriod(end.minusDays(7), end),
                new WeeklyReport(
                        end.minusDays(7),
                        end,
                        new BigDecimal("500"),
                        new BigDecimal("400"),
                        new BigDecimal("100"),
                        new BigDecimal("25"),
                        Map.of("Кафе", new BigDecimal("500")),
                        expensesCount,
                        null
                ),
                new ReportArtifact(
                        "expense-reports",
                        "weekly/42/report.xlsx",
                        "weekly.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        3,
                        "checksum",
                        "http://localhost:9000/report"
                ),
                UUID.randomUUID(),
                ZonedDateTime.now()
        );
    }
}
