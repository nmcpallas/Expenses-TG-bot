package com.cpallas.expenses.storage.repo;

import com.cpallas.expenses.storage.jpa.MonthlyReportJobJpa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

public interface MonthlyReportJobRepo extends JpaRepository<MonthlyReportJobJpa, UUID> {

    Optional<MonthlyReportJobJpa> findByChatIdAndPeriodStartAndPeriodEnd(Long chatId, LocalDate periodStart, LocalDate periodEnd);

    Optional<MonthlyReportJobJpa> findByReportId(UUID reportId);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            UPDATE MonthlyReportJobJpa job
            SET job.status = 'PROCESSING'
            WHERE job.chatId = :chatId
              AND job.periodStart = :periodStart
              AND job.periodEnd = :periodEnd
              AND job.status = 'REQUESTED'
            """)
    int claim(@Param("chatId") Long chatId,
              @Param("periodStart") LocalDate periodStart,
              @Param("periodEnd") LocalDate periodEnd);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            UPDATE MonthlyReportJobJpa job
            SET job.status = 'DELIVERED',
                job.reportId = :reportId,
                job.deliveredAt = :deliveredAt
            WHERE job.chatId = :chatId
              AND job.periodStart = :periodStart
              AND job.periodEnd = :periodEnd
              AND job.status = 'PROCESSING'
            """)
    int markDelivered(@Param("chatId") Long chatId,
                      @Param("periodStart") LocalDate periodStart,
                      @Param("periodEnd") LocalDate periodEnd,
                      @Param("reportId") UUID reportId,
                      @Param("deliveredAt") ZonedDateTime deliveredAt);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            UPDATE MonthlyReportJobJpa job
            SET job.status = 'REQUESTED'
            WHERE job.chatId = :chatId
              AND job.periodStart = :periodStart
              AND job.periodEnd = :periodEnd
              AND job.status = 'PROCESSING'
            """)
    int release(@Param("chatId") Long chatId,
                @Param("periodStart") LocalDate periodStart,
                @Param("periodEnd") LocalDate periodEnd);
}
