package com.cpallas.expenses.storage.jpa;

import com.cpallas.expenses.enums.EntitlementSource;
import com.cpallas.expenses.enums.EntitlementSubjectType;
import com.cpallas.expenses.enums.EntitlementValueType;
import com.cpallas.expenses.enums.Feature;
import com.cpallas.expenses.storage.ids.EntitlementId;
import com.cpallas.expenses.storage.jpa.base.AuditableBaseJpa;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Table(schema = "access", name = "entitlement")
@Getter
@Setter
@NoArgsConstructor
public class EntitlementJpa extends AuditableBaseJpa {

    @EmbeddedId
    private EntitlementId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false)
    private EntitlementSubjectType subjectType;

    @Column(name = "subject_id", nullable = false)
    private String subjectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Feature feature;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false)
    private EntitlementValueType valueType;

    @Column(nullable = false)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntitlementSource source;

    @Column(name = "valid_from")
    private ZonedDateTime validFrom;

    @Column(name = "valid_until")
    private ZonedDateTime validUntil;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at")
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
