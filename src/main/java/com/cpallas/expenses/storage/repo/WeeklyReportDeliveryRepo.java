package com.cpallas.expenses.storage.repo;

import com.cpallas.expenses.storage.jpa.WeeklyReportDeliveryJpa;
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

public interface WeeklyReportDeliveryRepo extends JpaRepository<WeeklyReportDeliveryJpa, UUID> {

    Optional<WeeklyReportDeliveryJpa> findByChatIdAndPeriodStartAndPeriodEnd(
            Long chatId,
            LocalDate periodStart,
            LocalDate periodEnd
    );

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            UPDATE WeeklyReportDeliveryJpa delivery
            SET delivery.status = 'PROCESSING'
            WHERE delivery.chatId = :chatId
              AND delivery.periodStart = :periodStart
              AND delivery.periodEnd = :periodEnd
              AND delivery.status = 'REQUESTED'
            """)
    int claim(@Param("chatId") Long chatId,
              @Param("periodStart") LocalDate periodStart,
              @Param("periodEnd") LocalDate periodEnd);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            UPDATE WeeklyReportDeliveryJpa delivery
            SET delivery.status = 'DELIVERED',
                delivery.reportId = :reportId,
                delivery.deliveredAt = :deliveredAt
            WHERE delivery.chatId = :chatId
              AND delivery.periodStart = :periodStart
              AND delivery.periodEnd = :periodEnd
              AND delivery.status = 'PROCESSING'
            """)
    int markDelivered(@Param("chatId") Long chatId,
                      @Param("periodStart") LocalDate periodStart,
                      @Param("periodEnd") LocalDate periodEnd,
                      @Param("reportId") UUID reportId,
                      @Param("deliveredAt") ZonedDateTime deliveredAt);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            UPDATE WeeklyReportDeliveryJpa delivery
            SET delivery.status = 'REQUESTED'
            WHERE delivery.chatId = :chatId
              AND delivery.periodStart = :periodStart
              AND delivery.periodEnd = :periodEnd
              AND delivery.status = 'PROCESSING'
            """)
    int release(@Param("chatId") Long chatId,
                @Param("periodStart") LocalDate periodStart,
                @Param("periodEnd") LocalDate periodEnd);
}
