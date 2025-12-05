package com.cpallas.expenses.storage.jpa.base;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

import java.time.ZonedDateTime;

@MappedSuperclass
public abstract class AuditableBaseJpa {

    @PrePersist
    protected void prePersist() {
        if (getCreatedAt() == null) {
            setCreatedAt(ZonedDateTime.now());
        }
    }

    public abstract void setCreatedAt(ZonedDateTime now);

    public abstract ZonedDateTime getCreatedAt();
}
