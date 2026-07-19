package com.cpallas.expenses.storage.jpa;

import com.cpallas.expenses.enums.SubscriptionPlan;
import com.cpallas.expenses.enums.SubscriptionStatus;
import com.cpallas.expenses.enums.SubscriptionType;
import com.cpallas.expenses.storage.ids.SubscriptionId;
import com.cpallas.expenses.storage.jpa.base.AuditableBaseJpa;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Table(schema = "billing", name = "subscription")
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionJpa extends AuditableBaseJpa {

    @EmbeddedId
    private SubscriptionId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private UserJpa owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    @Column(name = "valid_until")
    private ZonedDateTime validUntil;

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
