package com.payflow.banking.settlement;

import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "settlement.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentSettlementOutboxRelay {

    private final PaymentSettlementOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${settlement.outbox.max-retries:5}")
    private int maxRetries;

    @Scheduled(fixedDelayString = "${settlement.outbox.fixed-delay:2000}")
    @Transactional
    public void publishPendingEvents() {
        List<PaymentSettlementOutbox> events = outboxRepository
                .findTop50ByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(
                        List.of(SettlementOutboxStatus.PENDING, SettlementOutboxStatus.FAILED),
                        maxRetries
                );
        for (PaymentSettlementOutbox event : events) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload()).get(3, TimeUnit.SECONDS);
                event.markPublished();
            } catch (Exception exception) {
                event.markFailed(resolveMessage(exception));
            }
        }
    }

    private String resolveMessage(Exception exception) {
        Throwable cause = exception.getCause();
        String message = cause == null ? exception.getMessage() : cause.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message.substring(0, Math.min(message.length(), 500));
    }
}
