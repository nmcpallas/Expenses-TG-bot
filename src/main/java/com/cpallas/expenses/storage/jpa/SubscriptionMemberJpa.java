package com.cpallas.expenses.storage.jpa;

import com.cpallas.expenses.enums.SubscriptionMemberRole;
import com.cpallas.expenses.storage.ids.SubscriptionMemberId;
import com.cpallas.expenses.storage.jpa.base.AuditableBaseJpa;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Table(schema = "billing", name = "subscription_member")
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionMemberJpa extends AuditableBaseJpa {

    @EmbeddedId
    private SubscriptionMemberId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private SubscriptionJpa subscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserJpa user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionMemberRole role;

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
