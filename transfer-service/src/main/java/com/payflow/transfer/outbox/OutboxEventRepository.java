package com.payflow.transfer.outbox;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    long countByStatus(OutboxEventStatus status);

    long countByStatusAndRetryCountLessThan(OutboxEventStatus status, int retryCount);

    long countByStatusAndRetryCountGreaterThanEqual(OutboxEventStatus status, int retryCount);

    Optional<OutboxEvent> findFirstByStatusInOrderByCreatedAtAsc(Collection<OutboxEventStatus> statuses);

    List<OutboxEvent> findTop50ByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(
            Collection<OutboxEventStatus> statuses,
            int maxRetries
    );

    @Modifying
    @Query("""
            update OutboxEvent event
            set event.status = :processingStatus
              , event.processingStartedAt = :processingStartedAt
            where event.id = :eventId
              and event.status in :publishableStatuses
              and event.retryCount < :maxRetries
            """)
    int claimPublishableEvent(
            @Param("eventId") Long eventId,
            @Param("publishableStatuses") Collection<OutboxEventStatus> publishableStatuses,
            @Param("processingStatus") OutboxEventStatus processingStatus,
            @Param("processingStartedAt") LocalDateTime processingStartedAt,
            @Param("maxRetries") int maxRetries
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OutboxEvent event
            set event.status = :failedStatus
              , event.processingStartedAt = null
              , event.retryCount = event.retryCount + 1
              , event.lastError = :lastError
            where event.status = :processingStatus
              and event.processingStartedAt < :timeoutThreshold
            """)
    int recoverStuckProcessingEvents(
            @Param("processingStatus") OutboxEventStatus processingStatus,
            @Param("failedStatus") OutboxEventStatus failedStatus,
            @Param("timeoutThreshold") LocalDateTime timeoutThreshold,
            @Param("lastError") String lastError
    );
}
