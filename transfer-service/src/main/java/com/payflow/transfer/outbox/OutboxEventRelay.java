package com.payflow.transfer.outbox;

import java.time.Duration;
import java.time.LocalDateTime;
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
@ConditionalOnProperty(prefix = "outbox.publisher", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OutboxEventRelay {

    private static final List<OutboxEventStatus> PUBLISHABLE_STATUSES = List.of(
            OutboxEventStatus.PENDING,
            OutboxEventStatus.FAILED
    );

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${outbox.publisher.send-timeout:3s}")
    private Duration sendTimeout;

    @Value("${outbox.publisher.max-retries:5}")
    private int maxRetries;

    @Value("${outbox.publisher.processing-timeout:30s}")
    private Duration processingTimeout;

    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay:2000}")
    @Transactional
    public void publishPendingEvents() {
        recoverStuckProcessingEvents();
        outboxEventRepository.findTop50ByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(PUBLISHABLE_STATUSES, maxRetries)
                .forEach(event -> {
                    if (claim(event)) {
                        publishOne(event);
                    }
                });
    }

    private void recoverStuckProcessingEvents() {
        outboxEventRepository.recoverStuckProcessingEvents(
                OutboxEventStatus.PROCESSING,
                OutboxEventStatus.FAILED,
                LocalDateTime.now().minus(processingTimeout),
                "Outbox event processing timed out"
        );
    }

    private boolean claim(OutboxEvent event) {
        LocalDateTime processingStartedAt = LocalDateTime.now();
        int updated = outboxEventRepository.claimPublishableEvent(
                event.getId(),
                PUBLISHABLE_STATUSES,
                OutboxEventStatus.PROCESSING,
                processingStartedAt,
                maxRetries
        );
        if (updated == 1) {
            event.markProcessing(processingStartedAt);
            return true;
        }
        return false;
    }

    private void publishOne(OutboxEvent event) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload())
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
            event.markPublished();
        } catch (Exception exception) {
            event.markFailed(resolveMessage(exception));
        }
    }

    private String resolveMessage(Exception exception) {
        Throwable cause = exception.getCause();
        if (cause != null && cause.getMessage() != null) {
            return cause.getMessage();
        }
        return exception.getMessage();
    }
}
