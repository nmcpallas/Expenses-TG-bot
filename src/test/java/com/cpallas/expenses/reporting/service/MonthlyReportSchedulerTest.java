package com.cpallas.expenses.reporting.service;

import com.cpallas.expenses.reporting.contract.MonthlyReportRequested;
import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.jpa.ChatJpa;
import com.cpallas.expenses.storage.repo.ChatRepo;
import com.cpallas.expenses.storage.repo.MonthlyReportJobRepo;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthlyReportSchedulerTest {

    @Mock
    private ChatRepo chatRepo;
    @Mock
    private MonthlyReportJobRepo monthlyReportJobRepo;
    @Mock
    private AnalyticsEventPublisher publisher;

    private MonthlyReportScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new MonthlyReportScheduler(chatRepo, monthlyReportJobRepo, publisher);
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        ReflectionTestUtils.setField(scheduler, "zone", "Asia/Tashkent");
    }

    @Test
    void publishesReportForChatOnItsFinancialPeriodBoundary() {
        ChatJpa chat = chat(42L, 22);
        LocalDate today = LocalDate.of(2026, 7, 22);
        when(chatRepo.findAll()).thenReturn(List.of(chat));
        when(monthlyReportJobRepo.findByChatIdAndPeriodStartAndPeriodEnd(42L, LocalDate.of(2026, 6, 22), today))
                .thenReturn(Optional.empty());

        scheduler.scheduleForDate(today);

        ArgumentCaptor<MonthlyReportRequested> eventCaptor = ArgumentCaptor.forClass(MonthlyReportRequested.class);
        verify(monthlyReportJobRepo).saveAndFlush(any());
        verify(publisher).publish(eventCaptor.capture());
        MonthlyReportRequested event = eventCaptor.getValue();
        assertThat(event.chatId()).isEqualTo(42L);
        assertThat(event.period().start()).isEqualTo(LocalDate.of(2026, 6, 22));
        assertThat(event.period().end()).isEqualTo(today);
        assertThat(event.comparisonPeriod().start()).isEqualTo(LocalDate.of(2026, 5, 22));
        assertThat(event.comparisonPeriod().end()).isEqualTo(LocalDate.of(2026, 6, 22));
        assertThat(event.timezone()).isEqualTo("Asia/Tashkent");
        assertThat(event.eventType()).isEqualTo("MonthlyReportRequested");
    }

    @Test
    void doesNotPublishDuplicateOrNonMatchingChat() {
        LocalDate today = LocalDate.of(2026, 7, 22);
        ChatJpa duplicate = chat(42L, 22);
        ChatJpa otherDay = chat(43L, 1);
        when(chatRepo.findAll()).thenReturn(List.of(duplicate, otherDay));
        when(monthlyReportJobRepo.findByChatIdAndPeriodStartAndPeriodEnd(42L, LocalDate.of(2026, 6, 22), today))
                .thenReturn(Optional.of(new com.cpallas.expenses.storage.jpa.MonthlyReportJobJpa()));

        scheduler.scheduleForDate(today);

        verify(monthlyReportJobRepo, never()).saveAndFlush(any());
        verify(publisher, never()).publish(any(MonthlyReportRequested.class));
        verify(monthlyReportJobRepo, never()).findByChatIdAndPeriodStartAndPeriodEnd(eq(43L), any(), any());
    }

    private ChatJpa chat(Long id, int monthStart) {
        ChatJpa chat = new ChatJpa();
        chat.setId(new ChatId(id));
        chat.setMonthStart(monthStart);
        return chat;
    }
}
