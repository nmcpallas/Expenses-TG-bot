package com.cpallas.expenses.storage.repo;

import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.ids.SubscriptionChatId;
import com.cpallas.expenses.storage.jpa.SubscriptionChatJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionChatRepo extends JpaRepository<SubscriptionChatJpa, SubscriptionChatId> {

    List<SubscriptionChatJpa> findAllByChatId(ChatId chatId);
}
