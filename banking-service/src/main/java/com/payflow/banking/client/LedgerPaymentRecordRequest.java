package com.payflow.banking.client;

import java.math.BigDecimal;

public record LedgerPaymentRecordRequest(
        String sourceType,
        Long sourceId,
        String entryType,
        Long userId,
        BigDecimal amount
) {
}
