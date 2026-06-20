package com.payflow.banking.dto;

import com.payflow.banking.entity.PaymentCharge;
import java.math.BigDecimal;

public record TossChargeSummaryResponse(
        Long chargeId,
        Long userId,
        String providerCode,
        BigDecimal amount,
        String status,
        String failureCode,
        String failureReason,
        int compensationRetryCount,
        String compensationFailureReason,
        boolean ledgerRecorded,
        String ledgerRecordType,
        String ledgerFailureReason,
        int ledgerRetryCount
) {

    public static TossChargeSummaryResponse from(PaymentCharge charge) {
        return new TossChargeSummaryResponse(
                charge.getId(),
                charge.getUserId(),
                charge.getProviderCode(),
                charge.getAmount(),
                charge.getStatus().name(),
                charge.getFailureCode(),
                charge.getFailureReason(),
                charge.getCompensationRetryCount(),
                charge.getCompensationFailureReason(),
                charge.isLedgerRecorded(),
                charge.getLedgerRecordType(),
                charge.getLedgerFailureReason(),
                charge.getLedgerRetryCount()
        );
    }
}
