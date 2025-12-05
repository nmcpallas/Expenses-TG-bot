package com.cpallas.expenses.storage.jpa;

import com.cpallas.expenses.storage.jpa.base.AuditableBaseJpa;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(schema = "tg", name = "expense")
@Getter
@Setter
@NoArgsConstructor
public class ExpenseJpa extends AuditableBaseJpa {

    @Id
    private UUID id;

    @Column
    private Double amount;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name = "chat_id")
    private ChatJpa chat;

    @Column
    private String description;

    @Column(name = "created_at")
    private ZonedDateTime createdAt;
}
