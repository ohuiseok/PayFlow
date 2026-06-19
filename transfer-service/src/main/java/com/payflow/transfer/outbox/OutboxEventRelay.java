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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final PlatformTransactionManager transactionManager;

    @Value("${outbox.publisher.send-timeout:3s}")
    private Duration sendTimeout;

    @Value("${outbox.publisher.max-retries:5}")
    private int maxRetries;

    @Value("${outbox.publisher.processing-timeout:30s}")
    private Duration processingTimeout;

    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay:2000}")
    public void publishPendingEvents() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        List<OutboxEvent> events = transactionTemplate.execute(status -> {
            recoverStuckProcessingEvents();
            return outboxEventRepository.findTop50ByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(PUBLISHABLE_STATUSES, maxRetries);
        });
        if (events == null) {
            return;
        }
        events
                .forEach(event -> {
                    if (claim(transactionTemplate, event.getId())) {
                        String errorMessage = publishOne(event);
                        recordPublishResult(transactionTemplate, event.getId(), errorMessage);
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

    private boolean claim(TransactionTemplate transactionTemplate, Long eventId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> claimInTransaction(eventId)));
    }

    private boolean claimInTransaction(Long eventId) {
        LocalDateTime processingStartedAt = LocalDateTime.now();
        int updated = outboxEventRepository.claimPublishableEvent(
                eventId,
                PUBLISHABLE_STATUSES,
                OutboxEventStatus.PROCESSING,
                processingStartedAt,
                maxRetries
        );
        return updated == 1;
    }

    private String publishOne(OutboxEvent event) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload())
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
            return null;
        } catch (Exception exception) {
            return resolveMessage(exception);
        }
    }

    private void recordPublishResult(TransactionTemplate transactionTemplate, Long eventId, String errorMessage) {
        transactionTemplate.executeWithoutResult(status -> outboxEventRepository.findById(eventId)
                .ifPresent(event -> {
                    if (errorMessage == null) {
                        event.markPublished();
                    } else {
                        event.markFailed(errorMessage);
                    }
                }));
    }

    private String resolveMessage(Exception exception) {
        Throwable cause = exception.getCause();
        if (cause != null && cause.getMessage() != null) {
            return cause.getMessage();
        }
        return exception.getMessage();
    }
}
