package com.cpallas.expenses.storage.repo;

import com.cpallas.expenses.storage.jpa.ExtremeExpenseDeliveryJpa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.UUID;

public interface ExtremeExpenseDeliveryRepo extends JpaRepository<ExtremeExpenseDeliveryJpa, UUID> {

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query(value = """
            INSERT INTO tg.extreme_expense_delivery (
                event_id, expense_id, chat_id, status, claimed_at
            ) VALUES (
                :eventId, :expenseId, :chatId, 'PROCESSING', :claimedAt
            )
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int claim(@Param("eventId") UUID eventId,
              @Param("expenseId") UUID expenseId,
              @Param("chatId") Long chatId,
              @Param("claimedAt") ZonedDateTime claimedAt);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            UPDATE ExtremeExpenseDeliveryJpa delivery
            SET delivery.status = 'DELIVERED',
                delivery.deliveredAt = :deliveredAt
            WHERE delivery.eventId = :eventId
              AND delivery.status = 'PROCESSING'
            """)
    int markDelivered(@Param("eventId") UUID eventId,
                      @Param("deliveredAt") ZonedDateTime deliveredAt);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            DELETE FROM ExtremeExpenseDeliveryJpa delivery
            WHERE delivery.eventId = :eventId
              AND delivery.status = 'PROCESSING'
            """)
    int release(@Param("eventId") UUID eventId);
}
