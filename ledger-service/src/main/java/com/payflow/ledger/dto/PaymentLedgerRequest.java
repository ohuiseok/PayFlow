package com.payflow.ledger.dto;

import com.payflow.ledger.entity.LedgerEntryType;
import com.payflow.ledger.entity.LedgerSourceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record PaymentLedgerRequest(
        @NotNull LedgerSourceType sourceType,
        @NotNull Long sourceId,
        @NotNull LedgerEntryType entryType,
        @NotNull Long userId,
        @NotNull @Positive BigDecimal amount
) {
}
