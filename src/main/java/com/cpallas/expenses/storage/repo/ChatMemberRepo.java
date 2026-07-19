package com.cpallas.expenses.storage.repo;

import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.ids.ChatMemberId;
import com.cpallas.expenses.storage.ids.UserId;
import com.cpallas.expenses.storage.jpa.ChatMemberJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatMemberRepo extends JpaRepository<ChatMemberJpa, ChatMemberId> {

    Optional<ChatMemberJpa> findByChatIdAndUserId(ChatId chatId, UserId userId);
}
