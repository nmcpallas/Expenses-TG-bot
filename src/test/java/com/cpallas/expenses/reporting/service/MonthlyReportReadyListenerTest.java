package com.cpallas.expenses.reporting.service;

import com.cpallas.expenses.reporting.contract.MonthlyReport;
import com.cpallas.expenses.reporting.contract.MonthlyReportReady;
import com.cpallas.expenses.reporting.contract.ReportPeriod;
import com.cpallas.expenses.storage.jpa.MonthlyReportJobJpa;
import com.cpallas.expenses.storage.repo.MonthlyReportJobRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthlyReportReadyListenerTest {

    @Mock
    private MonthlyReportJobRepo monthlyReportJobRepo;
    @Mock
    private TelegramClient telegramClient;

    private MonthlyReportReadyListener listener;

    @BeforeEach
    void setUp() {
        listener = new MonthlyReportReadyListener(monthlyReportJobRepo, new MonthlyReportMessageFormatter(), telegramClient);
    }

    @Test
    void sendsReportAndMarksJobDelivered() throws Exception {
        MonthlyReportReady event = event();
        MonthlyReportJobJpa job = new MonthlyReportJobJpa();
        job.setStatus("REQUESTED");
        when(monthlyReportJobRepo.findByChatIdAndPeriodStartAndPeriodEnd(42L, event.period().start(), event.period().end()))
                .thenReturn(Optional.of(job));

        listener.receive(event);

        verify(telegramClient).execute(any(SendMessage.class));
        assertThat(job.getStatus()).isEqualTo("DELIVERED");
        assertThat(job.getReportId()).isEqualTo(event.reportId());
        assertThat(job.getDeliveredAt()).isNotNull();
    }

    @Test
    void skipsAlreadyDeliveredReport() throws Exception {
        MonthlyReportReady event = event();
        MonthlyReportJobJpa job = new MonthlyReportJobJpa();
        job.setStatus("DELIVERED");
        when(monthlyReportJobRepo.findByChatIdAndPeriodStartAndPeriodEnd(42L, event.period().start(), event.period().end()))
                .thenReturn(Optional.of(job));

        listener.receive(event);

        verify(telegramClient, never()).execute(any(SendMessage.class));
    }

    private MonthlyReportReady event() {
        return new MonthlyReportReady(
                UUID.randomUUID(), "MonthlyReportReady", 1, UUID.randomUUID(), 42L,
                new ReportPeriod(LocalDate.of(2026, 6, 22), LocalDate.of(2026, 7, 22)),
                new MonthlyReport(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, null, null, null, List.of(), List.of()),
                UUID.randomUUID(), ZonedDateTime.now()
        );
    }
}
