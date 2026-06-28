package com.payflow.banking.settlement;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentSettlementEvent(
        String eventId,
        PaymentSettlementEventType type,
        Long chargeId,
        Long userId,
        String paymentKey,
        BigDecimal amount,
        String currency,
        String ledgerSourceType,
        LocalDateTime occurredAt
) {
}
