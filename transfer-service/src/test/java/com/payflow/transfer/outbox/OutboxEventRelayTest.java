package com.payflow.transfer.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "outbox.publisher.enabled=true",
        "outbox.publisher.max-retries=2"
})
class OutboxEventRelayTest {

    @Autowired
    OutboxEventRelay outboxEventRelay;

    @Autowired
    OutboxEventRepository outboxEventRepository;

    @MockitoBean
    KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
    }

    @Test
    void publishPendingEventsMarksEventPublishedAfterKafkaSend() {
        OutboxEvent event = outboxEventRepository.save(new OutboxEvent("transfer.completed", "1", "{}"));
        var record = new ProducerRecord<String, String>("transfer.completed", "1", "{}");
        when(kafkaTemplate.send(eq("transfer.completed"), eq("1"), eq("{}")))
                .thenReturn(CompletableFuture.completedFuture(new SendResult<>(record, null)));

        outboxEventRelay.publishPendingEvents();

        OutboxEvent published = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(published.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(published.getPublishedAt()).isNotNull();
        assertThat(published.getRetryCount()).isZero();
    }

    @Test
    void publishPendingEventsMarksEventFailedWhenKafkaSendFails() {
        OutboxEvent event = outboxEventRepository.save(new OutboxEvent("transfer.completed", "1", "{}"));
        when(kafkaTemplate.send(eq("transfer.completed"), eq("1"), eq("{}")))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));

        outboxEventRelay.publishPendingEvents();

        OutboxEvent failed = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(failed.getRetryCount()).isEqualTo(1);
        assertThat(failed.getLastError()).contains("kafka down");
    }

    @Test
    void publishPendingEventsSkipsEventsThatExceededRetryLimit() {
        OutboxEvent event = new OutboxEvent("transfer.completed", "1", "{}");
        event.markFailed("first failure");
        event.markFailed("second failure");
        outboxEventRepository.save(event);

        outboxEventRelay.publishPendingEvents();

        OutboxEvent skipped = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertThat(skipped.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(skipped.getRetryCount()).isEqualTo(2);
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }
}
