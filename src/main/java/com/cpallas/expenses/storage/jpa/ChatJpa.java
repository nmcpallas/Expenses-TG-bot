package com.cpallas.expenses.storage.jpa;

import com.cpallas.expenses.ids.ChatId;
import com.cpallas.expenses.storage.jpa.base.AuditableBaseJpa;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(schema = "tg", name = "chat")
@Getter
@Setter
@NoArgsConstructor
public class ChatJpa extends AuditableBaseJpa {

    @EmbeddedId
    private ChatId id;

    @Column(name = "month_limit")
    private Double monthLimit;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserJpa user;

    @Setter(value = AccessLevel.PRIVATE)
    @OneToMany(fetch=FetchType.LAZY)
    @JoinColumn(name = "chat_id")
    private List<ExpenseJpa> expenses = new ArrayList<>();

    @Column(name = "created_at")
    protected ZonedDateTime createdAt;

    @Version
    private Long version;
}
