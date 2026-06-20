package com.payflow.banking.toss;

import com.payflow.banking.entity.TossPaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "toss-payments", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockTossPaymentsClient implements TossPaymentsClient {

    @Override
    public TossPaymentResult confirm(String paymentKey, String orderId, BigDecimal amount) {
        return new TossPaymentResult(
                paymentKey,
                orderId,
                "PayFlow credit charge",
                "간편결제",
                TossPaymentStatus.DONE,
                amount,
                amount,
                LocalDateTime.now(),
                "https://dashboard.tosspayments.com/receipt/mock/" + paymentKey,
                null,
                "{\"mode\":\"mock\",\"status\":\"DONE\"}"
        );
    }

    @Override
    public TossPaymentResult getPayment(String paymentKey) {
        return new TossPaymentResult(
                paymentKey,
                null,
                "PayFlow credit charge",
                "간편결제",
                TossPaymentStatus.DONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                LocalDateTime.now(),
                "https://dashboard.tosspayments.com/receipt/mock/" + paymentKey,
                null,
                "{\"mode\":\"mock\",\"status\":\"DONE\"}"
        );
    }

    @Override
    public TossPaymentCancelResult cancel(String paymentKey, String cancelReason, BigDecimal cancelAmount) {
        TossPaymentResult payment = new TossPaymentResult(
                paymentKey,
                null,
                "PayFlow credit charge",
                "간편결제",
                TossPaymentStatus.CANCELED,
                cancelAmount,
                BigDecimal.ZERO,
                LocalDateTime.now(),
                "https://dashboard.tosspayments.com/receipt/mock/" + paymentKey,
                null,
                "{\"mode\":\"mock\",\"status\":\"CANCELED\"}"
        );
        return new TossPaymentCancelResult(payment, cancelAmount, "mock-cancel-" + paymentKey);
    }
}
