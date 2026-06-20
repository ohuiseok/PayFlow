package com.payflow.banking.dto;

import java.util.Map;

public record TossPaymentWebhookRequest(
        String eventType,
        String paymentKey,
        String orderId,
        String transactionKey,
        String status,
        Map<String, Object> payload
) {
}
