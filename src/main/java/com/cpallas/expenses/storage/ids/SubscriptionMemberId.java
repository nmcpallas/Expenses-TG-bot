package com.cpallas.expenses.storage.ids;

import jakarta.persistence.Column;
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
public final class SubscriptionMemberId implements Serializable {

    @Column(name = "id")
    private UUID id;

    public SubscriptionMemberId(UUID id) {
        this.id = id;
    }
}
