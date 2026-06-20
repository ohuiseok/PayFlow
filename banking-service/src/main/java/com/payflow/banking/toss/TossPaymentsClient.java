package com.payflow.banking.toss;

import java.math.BigDecimal;

public interface TossPaymentsClient {

    TossPaymentResult confirm(String paymentKey, String orderId, BigDecimal amount);

    TossPaymentResult getPayment(String paymentKey);

    TossPaymentCancelResult cancel(String paymentKey, String cancelReason, BigDecimal cancelAmount);
}
