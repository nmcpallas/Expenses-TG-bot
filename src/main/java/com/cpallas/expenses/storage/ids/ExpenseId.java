package com.cpallas.expenses.storage.ids;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@EqualsAndHashCode
@Getter
@NoArgsConstructor(onConstructor_ = @Deprecated)
public final class ExpenseId implements Serializable {

    private UUID id;

    public ExpenseId(UUID id) {
        this.id = id;
    }
}
