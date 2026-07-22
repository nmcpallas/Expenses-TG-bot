package com.cpallas.expenses.storage.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(schema = "tg", name = "monthly_report_jobs")
@Getter
@Setter
@NoArgsConstructor
public class MonthlyReportJobJpa {

    @Id
    private UUID id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "report_id")
    private UUID reportId;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "delivered_at")
    private ZonedDateTime deliveredAt;
}
