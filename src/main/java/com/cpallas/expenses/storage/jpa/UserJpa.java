package com.cpallas.expenses.storage.jpa;

import com.cpallas.expenses.storage.ids.UserId;
import com.cpallas.expenses.storage.jpa.base.AuditableBaseJpa;
import com.cpallas.expenses.enums.GlobalRole;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "global_role", nullable = false)
    private GlobalRole globalRole = GlobalRole.USER;

    @Column(name = "created_at")
    protected ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @Version
    private Long version;
}
