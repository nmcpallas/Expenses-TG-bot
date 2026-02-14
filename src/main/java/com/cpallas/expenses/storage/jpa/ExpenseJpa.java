package com.cpallas.expenses.storage.jpa;

import com.cpallas.expenses.storage.ids.ExpenseId;
import com.cpallas.expenses.storage.jpa.base.AuditableBaseJpa;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(schema = "tg", name = "expense")
@Getter
@Setter
@NoArgsConstructor
public class ExpenseJpa extends AuditableBaseJpa {

    @EmbeddedId
    private ExpenseId id;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name = "chat_id")
    private ChatJpa chat;

    @Column
    private String description;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private CategoryJpa category;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @Column(name = "created_at")
    private ZonedDateTime createdAt;
}
