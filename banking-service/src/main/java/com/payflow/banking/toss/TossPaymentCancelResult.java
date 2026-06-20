package com.payflow.banking.toss;

import java.math.BigDecimal;

public record TossPaymentCancelResult(
        TossPaymentResult payment,
        BigDecimal canceledAmount,
        String transactionKey
) {
}
