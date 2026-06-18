package com.payflow.transfer.outbox;

import java.time.Duration;
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

    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay:2000}")
    @Transactional
    public void publishPendingEvents() {
        outboxEventRepository.findTop50ByStatusInOrderByCreatedAtAsc(PUBLISHABLE_STATUSES)
                .forEach(this::publishOne);
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
