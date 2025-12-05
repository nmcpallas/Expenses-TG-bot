package com.cpallas.expenses.storage.jpa;

import com.cpallas.expenses.ids.UserId;
import com.cpallas.expenses.storage.jpa.base.AuditableBaseJpa;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Table(schema = "tg", name = "user")
@Getter
@Setter
@NoArgsConstructor
public class UserJpa extends AuditableBaseJpa {

    @EmbeddedId
    private UserId id;

    @Column(name = "created_at")
    protected ZonedDateTime createdAt;

    @Version
    private Long version;
}
