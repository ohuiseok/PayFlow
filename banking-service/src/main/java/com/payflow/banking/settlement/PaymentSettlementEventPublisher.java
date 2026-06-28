package com.payflow.banking.settlement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.banking.entity.PaymentCharge;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentSettlementEventPublisher {

    private final PaymentSettlementOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Value("${topics.payment-settlement:payment.settlement}")
    private String topic;

    @Value("${settlement.zone:Asia/Seoul}")
    private String settlementZone;

    public void publishCharge(PaymentCharge charge, String paymentKey, LocalDateTime occurredAt) {
        publish(new PaymentSettlementEvent(
                "TOSS_CHARGE:" + charge.getId(),
                PaymentSettlementEventType.CHARGE,
                charge.getId(),
                charge.getUserId(),
                paymentKey,
                charge.getAmount(),
                charge.getCurrency(),
                "TOSS_CHARGE",
                occurredAt == null ? now() : occurredAt
        ));
    }

    public void publishCancel(
            PaymentCharge charge,
            String paymentKey,
            String transactionKey,
            BigDecimal canceledAmount,
            LocalDateTime occurredAt
    ) {
        String cancelKey = transactionKey == null || transactionKey.isBlank()
                ? charge.getId().toString()
                : transactionKey;
        publish(new PaymentSettlementEvent(
                "TOSS_CANCEL:" + cancelKey,
                PaymentSettlementEventType.CANCEL,
                charge.getId(),
                charge.getUserId(),
                paymentKey,
                canceledAmount,
                charge.getCurrency(),
                "TOSS_CANCEL",
                occurredAt == null ? now() : occurredAt
        ));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of(settlementZone));
    }

    private void publish(PaymentSettlementEvent event) {
        if (outboxRepository.existsByEventId(event.eventId())) {
            return;
        }
        try {
            outboxRepository.save(new PaymentSettlementOutbox(
                    event.eventId(), topic, event.chargeId().toString(), objectMapper.writeValueAsString(event)
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize payment settlement event", exception);
        }
    }
}
