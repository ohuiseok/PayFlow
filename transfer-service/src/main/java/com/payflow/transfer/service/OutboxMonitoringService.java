package com.payflow.transfer.service;

import com.payflow.transfer.dto.OutboxStatusCountResponse;
import com.payflow.transfer.dto.OutboxSummaryResponse;
import com.payflow.transfer.outbox.OutboxEvent;
import com.payflow.transfer.outbox.OutboxEventRepository;
import com.payflow.transfer.outbox.OutboxEventStatus;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxMonitoringService {

    private static final List<OutboxEventStatus> PENDING_STATUSES = List.of(
            OutboxEventStatus.PENDING,
            OutboxEventStatus.FAILED,
            OutboxEventStatus.PROCESSING
    );

    private final OutboxEventRepository outboxEventRepository;

    @Value("${outbox.publisher.max-retries:5}")
    private int maxRetries;

    @Transactional(readOnly = true)
    public OutboxSummaryResponse getSummary() {
        List<OutboxStatusCountResponse> statusCounts = Arrays.stream(OutboxEventStatus.values())
                .map(status -> new OutboxStatusCountResponse(status, outboxEventRepository.countByStatus(status)))
                .toList();
        OutboxEvent oldestPending = outboxEventRepository.findFirstByStatusInOrderByCreatedAtAsc(PENDING_STATUSES)
                .orElse(null);

        return new OutboxSummaryResponse(
                outboxEventRepository.count(),
                statusCounts,
                outboxEventRepository.countByStatusAndRetryCountLessThan(OutboxEventStatus.FAILED, maxRetries),
                outboxEventRepository.countByStatusAndRetryCountGreaterThanEqual(OutboxEventStatus.FAILED, maxRetries),
                resolveOldestPendingAgeSeconds(oldestPending),
                oldestPending == null ? null : oldestPending.getCreatedAt()
        );
    }

    private Long resolveOldestPendingAgeSeconds(OutboxEvent oldestPending) {
        if (oldestPending == null) {
            return null;
        }
        return Duration.between(oldestPending.getCreatedAt(), LocalDateTime.now()).toSeconds();
    }
}
