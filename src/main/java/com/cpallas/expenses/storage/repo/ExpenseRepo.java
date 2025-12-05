package com.cpallas.expenses.storage.repo;

import com.cpallas.expenses.storage.jpa.ExpenseJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ExpenseRepo extends JpaRepository<ExpenseJpa, UUID> {
}
