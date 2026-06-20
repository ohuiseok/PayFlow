package com.payflow.banking.toss;

import com.payflow.banking.entity.TossPaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TossPaymentResult(
        String paymentKey,
        String orderId,
        String orderName,
        String method,
        TossPaymentStatus status,
        BigDecimal totalAmount,
        BigDecimal balanceAmount,
        LocalDateTime approvedAt,
        String receiptUrl,
        String checkoutUrl,
        String rawJson
) {
}
