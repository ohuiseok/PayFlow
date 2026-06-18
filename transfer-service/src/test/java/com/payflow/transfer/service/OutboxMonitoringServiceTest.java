package com.payflow.transfer.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.payflow.transfer.outbox.OutboxEvent;
import com.payflow.transfer.outbox.OutboxEventRepository;
import com.payflow.transfer.outbox.OutboxEventStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "outbox.publisher.max-retries=2")
class OutboxMonitoringServiceTest {

    @Autowired
    OutboxMonitoringService outboxMonitoringService;

    @Autowired
    OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
    }

    @Test
    void getSummaryReturnsStatusCountsAndRetryState() {
        outboxEventRepository.save(new OutboxEvent("transfer.completed", "1", "{}"));
        OutboxEvent processing = new OutboxEvent("transfer.completed", "2", "{}");
        processing.markProcessing();
        outboxEventRepository.save(processing);
        OutboxEvent published = new OutboxEvent("transfer.completed", "3", "{}");
        published.markPublished();
        outboxEventRepository.save(published);
        OutboxEvent retryableFailed = new OutboxEvent("transfer.completed", "4", "{}");
        retryableFailed.markFailed("first failure");
        outboxEventRepository.save(retryableFailed);
        OutboxEvent exhaustedFailed = new OutboxEvent("transfer.completed", "5", "{}");
        exhaustedFailed.markFailed("first failure");
        exhaustedFailed.markFailed("second failure");
        outboxEventRepository.save(exhaustedFailed);

        var summary = outboxMonitoringService.getSummary();

        assertThat(summary.totalCount()).isEqualTo(5);
        assertThat(summary.statusCounts())
                .filteredOn(count -> count.status() == OutboxEventStatus.PENDING)
                .singleElement()
                .extracting("count")
                .isEqualTo(1L);
        assertThat(summary.statusCounts())
                .filteredOn(count -> count.status() == OutboxEventStatus.PROCESSING)
                .singleElement()
                .extracting("count")
                .isEqualTo(1L);
        assertThat(summary.statusCounts())
                .filteredOn(count -> count.status() == OutboxEventStatus.PUBLISHED)
                .singleElement()
                .extracting("count")
                .isEqualTo(1L);
        assertThat(summary.statusCounts())
                .filteredOn(count -> count.status() == OutboxEventStatus.FAILED)
                .singleElement()
                .extracting("count")
                .isEqualTo(2L);
        assertThat(summary.retryableFailureCount()).isEqualTo(1);
        assertThat(summary.retryExhaustedCount()).isEqualTo(1);
        assertThat(summary.oldestPendingEventAgeSeconds()).isNotNull();
        assertThat(summary.oldestPendingEventCreatedAt()).isNotNull();
    }

    @Test
    void getSummaryReturnsNullOldestPendingWhenNoUnpublishedEventsExist() {
        OutboxEvent published = new OutboxEvent("transfer.completed", "1", "{}");
        published.markPublished();
        outboxEventRepository.save(published);

        var summary = outboxMonitoringService.getSummary();

        assertThat(summary.totalCount()).isEqualTo(1);
        assertThat(summary.oldestPendingEventAgeSeconds()).isNull();
        assertThat(summary.oldestPendingEventCreatedAt()).isNull();
    }
}
