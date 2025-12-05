package com.cpallas.expenses.ids;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@Getter
@NoArgsConstructor(onConstructor_ = @Deprecated)
public final class ChatId implements Serializable {

    @Column(name = "id")
    private Long id;

    public ChatId(Long id) {
        this.id = id;
    }
}
