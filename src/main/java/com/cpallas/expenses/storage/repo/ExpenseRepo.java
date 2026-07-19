package com.cpallas.expenses.storage.repo;

import com.cpallas.expenses.storage.jpa.ExpenseJpa;
import com.cpallas.expenses.storage.ids.ChatId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ExpenseRepo extends JpaRepository<ExpenseJpa, UUID>, QuerydslPredicateExecutor<ExpenseJpa> {

    @Query("""
            select expense
            from ExpenseJpa expense
            join fetch expense.chat
            join fetch expense.category
            where expense.chat.id = :chatId
              and expense.amount is not null
            """)
    List<ExpenseJpa> findTrainingExamplesByChatId(@Param("chatId") ChatId chatId);
}
