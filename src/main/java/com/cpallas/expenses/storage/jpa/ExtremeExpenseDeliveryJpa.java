package com.cpallas.expenses.storage.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(schema = "tg", name = "extreme_expense_delivery")
@Getter
@Setter
@NoArgsConstructor
public class ExtremeExpenseDeliveryJpa {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "expense_id", nullable = false, unique = true)
    private UUID expenseId;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Column(nullable = false)
    private String status;

    @Column(name = "claimed_at", nullable = false)
    private ZonedDateTime claimedAt;

    @Column(name = "delivered_at")
    private ZonedDateTime deliveredAt;
}
