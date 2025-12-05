package com.cpallas.expenses.storage.repo;

import com.cpallas.expenses.ids.ChatId;
import com.cpallas.expenses.storage.jpa.ChatJpa;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatRepo extends JpaRepository<ChatJpa, ChatId> {

    Optional<ChatJpa> findById(@NotNull ChatId id);
}
