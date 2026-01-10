package com.cpallas.expenses.storage.ids;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@EqualsAndHashCode
@Getter
@NoArgsConstructor(onConstructor_ = @Deprecated)
public final class ChatId implements Serializable {

    @Column(name = "id")
    private Long id;

    public ChatId(Long id) {
        this.id = id;
    }
}
