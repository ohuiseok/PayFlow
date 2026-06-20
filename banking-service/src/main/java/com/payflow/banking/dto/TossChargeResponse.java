package com.payflow.banking.dto;

import com.payflow.banking.entity.PaymentCharge;
import com.payflow.banking.entity.TossPaymentOrder;
import java.math.BigDecimal;

public record TossChargeResponse(
        Long chargeId,
        String providerCode,
        String orderId,
        String paymentKey,
        BigDecimal amount,
        String status,
        String tossStatus,
        Long walletId,
        Long walletTransactionId,
        String failureCode,
        String failureReason,
        int compensationRetryCount,
        String compensationFailureReason,
        boolean ledgerRecorded,
        String ledgerRecordType,
        String ledgerFailureReason,
        int ledgerRetryCount,
        String receiptUrl
) {

    public static TossChargeResponse from(PaymentCharge charge, TossPaymentOrder order) {
        return new TossChargeResponse(
                charge.getId(),
                charge.getProviderCode(),
                order.getTossOrderId(),
                order.getPaymentKey(),
                charge.getAmount(),
                charge.getStatus().name(),
                order.getTossStatus().name(),
                charge.getWalletId(),
                charge.getWalletTransactionId(),
                charge.getFailureCode(),
                charge.getFailureReason(),
                charge.getCompensationRetryCount(),
                charge.getCompensationFailureReason(),
                charge.isLedgerRecorded(),
                charge.getLedgerRecordType(),
                charge.getLedgerFailureReason(),
                charge.getLedgerRetryCount(),
                order.getReceiptUrl()
        );
    }
}
