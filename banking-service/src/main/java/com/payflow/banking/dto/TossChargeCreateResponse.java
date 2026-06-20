package com.payflow.banking.dto;

import com.payflow.banking.entity.PaymentCharge;
import com.payflow.banking.entity.TossPaymentOrder;
import java.math.BigDecimal;

public record TossChargeCreateResponse(
        Long chargeId,
        String providerCode,
        String orderId,
        String orderName,
        BigDecimal amount,
        String currency,
        String status,
        String customerKey
) {

    public static TossChargeCreateResponse from(PaymentCharge charge, TossPaymentOrder order) {
        return new TossChargeCreateResponse(
                charge.getId(),
                charge.getProviderCode(),
                order.getTossOrderId(),
                order.getOrderName(),
                charge.getAmount(),
                charge.getCurrency(),
                charge.getStatus().name(),
                "user-" + charge.getUserId()
        );
    }
}
