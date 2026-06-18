package com.payflow.transfer.dto;

import com.payflow.transfer.outbox.OutboxEventStatus;

public record OutboxStatusCountResponse(
        OutboxEventStatus status,
        long count
) {
}
