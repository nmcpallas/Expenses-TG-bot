package com.cpallas.expenses.storage.jpa;

import com.cpallas.expenses.storage.ids.CategoryId;
import com.cpallas.expenses.storage.jpa.base.AuditableBaseJpa;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.ZonedDateTime;
import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = false)
@Data
@Entity
@Table(name = "category", schema = "tg")
public class CategoryJpa extends AuditableBaseJpa {

    @EmbeddedId
    private CategoryId id;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name = "chat_id")
    private ChatJpa chat;

    @Column
    private String name;

    @Column(name = "spending_limit", precision = 12, scale = 2)
    private BigDecimal spendingLimit;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @Version
    private Long version;
}
