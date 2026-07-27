package com.cpallas.expenses.reporting.service;

import com.cpallas.expenses.reporting.contract.WeeklyReportRequested;
import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.jpa.ChatJpa;
import com.cpallas.expenses.storage.repo.ChatRepo;
import com.cpallas.expenses.storage.repo.WeeklyReportDeliveryRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklyReportSchedulerTest {

    @Mock
    private ChatRepo chatRepo;
    @Mock
    private WeeklyReportDeliveryRepo deliveryRepo;
    @Mock
    private AnalyticsEventPublisher publisher;

    private WeeklyReportScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new WeeklyReportScheduler(chatRepo, deliveryRepo, publisher);
        ReflectionTestUtils.setField(scheduler, "zone", "Asia/Tashkent");
    }

    @Test
    void storesRequestAndPublishesOnlyFilterParameters() {
        LocalDate periodEnd = LocalDate.of(2026, 7, 27);
        ChatJpa chat = new ChatJpa();
        chat.setId(new ChatId(42L));
        chat.setWeeklyReportEnabled(true);
        when(chatRepo.findAll()).thenReturn(List.of(chat));
        when(deliveryRepo.findByChatIdAndPeriodStartAndPeriodEnd(
                42L,
                periodEnd.minusDays(7),
                periodEnd
        )).thenReturn(Optional.empty());

        scheduler.sendForDate(periodEnd);

        verify(deliveryRepo).save(any());
        ArgumentCaptor<WeeklyReportRequested> event = ArgumentCaptor.forClass(WeeklyReportRequested.class);
        verify(publisher).publish(event.capture());
        assertThat(event.getValue().chatId()).isEqualTo(42L);
        assertThat(event.getValue().period().start()).isEqualTo(periodEnd.minusDays(7));
        assertThat(event.getValue().period().end()).isEqualTo(periodEnd);
        assertThat(event.getValue().comparisonPeriod().start()).isEqualTo(periodEnd.minusDays(14));
        assertThat(event.getValue().comparisonPeriod().end()).isEqualTo(periodEnd.minusDays(7));
        assertThat(event.getValue().timezone()).isEqualTo("Asia/Tashkent");
    }

    @Test
    void skipsAlreadyRequestedPeriod() {
        LocalDate periodEnd = LocalDate.of(2026, 7, 27);
        ChatJpa chat = new ChatJpa();
        chat.setId(new ChatId(42L));
        chat.setWeeklyReportEnabled(true);
        when(chatRepo.findAll()).thenReturn(List.of(chat));
        when(deliveryRepo.findByChatIdAndPeriodStartAndPeriodEnd(
                42L,
                periodEnd.minusDays(7),
                periodEnd
        )).thenReturn(Optional.of(new com.cpallas.expenses.storage.jpa.WeeklyReportDeliveryJpa()));

        scheduler.sendForDate(periodEnd);

        verify(deliveryRepo, never()).save(any());
        verify(publisher, never()).publish(any(WeeklyReportRequested.class));
    }
}
