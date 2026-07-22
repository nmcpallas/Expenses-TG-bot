package com.cpallas.expenses.storage.repo;

import com.cpallas.expenses.storage.jpa.MonthlyReportJobJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface MonthlyReportJobRepo extends JpaRepository<MonthlyReportJobJpa, UUID> {

    Optional<MonthlyReportJobJpa> findByChatIdAndPeriodStartAndPeriodEnd(Long chatId, LocalDate periodStart, LocalDate periodEnd);

    Optional<MonthlyReportJobJpa> findByReportId(UUID reportId);
}
