package com.payflow.transfer.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.payflow.transfer.entity.Transfer;
import com.payflow.transfer.outbox.OutboxEventRepository;
import com.payflow.transfer.outbox.OutboxEventStatus;
import com.payflow.transfer.repository.TransferRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TransferEventPublisherTest {

    @Autowired
    TransferEventPublisher transferEventPublisher;

    @Autowired
    OutboxEventRepository outboxEventRepository;

    @Autowired
    TransferRepository transferRepository;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        transferRepository.deleteAll();
    }

    @Test
    void publishCompletedStoresOutboxEvent() {
        Transfer transfer = transferRepository.save(new Transfer(1L, 2L, new BigDecimal("3000"), "key-1", "hash"));
        transferEventPublisher.publishCompleted(transfer);

        var events = outboxEventRepository.findAll();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getTopic()).isEqualTo("transfer.completed");
        assertThat(events.get(0).getEventKey()).isEqualTo(transfer.getId().toString());
        assertThat(events.get(0).getPayload()).contains("\"senderUserId\":1");
        assertThat(events.get(0).getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }
}
