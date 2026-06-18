package com.payflow.transfer.outbox;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findTop50ByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(
            Collection<OutboxEventStatus> statuses,
            int maxRetries
    );

    @Modifying
    @Query("""
            update OutboxEvent event
            set event.status = :processingStatus
            where event.id = :eventId
              and event.status in :publishableStatuses
              and event.retryCount < :maxRetries
            """)
    int claimPublishableEvent(
            @Param("eventId") Long eventId,
            @Param("publishableStatuses") Collection<OutboxEventStatus> publishableStatuses,
            @Param("processingStatus") OutboxEventStatus processingStatus,
            @Param("maxRetries") int maxRetries
    );
}
