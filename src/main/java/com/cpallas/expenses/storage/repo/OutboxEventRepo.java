package com.cpallas.expenses.storage.repo;

import com.cpallas.expenses.storage.jpa.OutboxEventJpa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepo extends JpaRepository<OutboxEventJpa, UUID> {

    List<OutboxEventJpa> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            UPDATE OutboxEventJpa event
            SET event.publishedAt = :publishedAt,
                event.attemptCount = event.attemptCount + 1
            WHERE event.id = :id
              AND event.publishedAt IS NULL
            """)
    int markPublished(@Param("id") UUID id, @Param("publishedAt") ZonedDateTime publishedAt);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
            UPDATE OutboxEventJpa event
            SET event.attemptCount = event.attemptCount + 1
            WHERE event.id = :id
              AND event.publishedAt IS NULL
            """)
    int markFailedAttempt(@Param("id") UUID id);
}
