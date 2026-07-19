package com.cpallas.expenses.storage.jpa;

import com.cpallas.expenses.storage.ids.SubscriptionChatId;
import com.cpallas.expenses.storage.jpa.base.AuditableBaseJpa;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Table(schema = "billing", name = "subscription_chat")
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionChatJpa extends AuditableBaseJpa {

    @EmbeddedId
    private SubscriptionChatId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private SubscriptionJpa subscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    private ChatJpa chat;

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
