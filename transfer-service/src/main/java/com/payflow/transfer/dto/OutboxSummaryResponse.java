package com.payflow.transfer.dto;

import java.time.LocalDateTime;
import java.util.List;

public record OutboxSummaryResponse(
        long totalCount,
        List<OutboxStatusCountResponse> statusCounts,
        long retryableFailureCount,
        long retryExhaustedCount,
        Long oldestPendingEventAgeSeconds,
        LocalDateTime oldestPendingEventCreatedAt
) {
}
