package com.cpallas.expenses.storage.jpa;

import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.jpa.base.AuditableBaseJpa;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
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

    @Column(name = "month_limit", precision = 12, scale = 2)
    private BigDecimal monthLimit;

    @Column(name = "month_start", precision = 12, scale = 2)
    private Integer monthStart;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserJpa user;

    @Setter(value = AccessLevel.PRIVATE)
    @OneToMany(fetch=FetchType.LAZY)
    @JoinColumn(name = "chat_id")
    private List<ExpenseJpa> expenses = new ArrayList<>();

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @Column(name = "created_at")
    protected ZonedDateTime createdAt;

    @Version
    private Long version;
}
