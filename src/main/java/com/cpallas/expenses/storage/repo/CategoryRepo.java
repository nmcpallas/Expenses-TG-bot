package com.cpallas.expenses.storage.repo;

import com.cpallas.expenses.storage.ids.CategoryId;
import com.cpallas.expenses.storage.ids.ChatId;
import com.cpallas.expenses.storage.jpa.CategoryJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepo extends JpaRepository<CategoryJpa, CategoryId> {

    List<CategoryJpa> findAllByChatId(ChatId chatId);
}
