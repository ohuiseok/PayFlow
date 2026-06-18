package com.payflow.ledger.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.payflow.ledger.entity.LedgerLineType;
import com.payflow.ledger.repository.LedgerEntryRepository;
import com.payflow.ledger.repository.LedgerLineRepository;
import com.payflow.ledger.repository.TransferFailureEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TransferEventConsumerTest {

    @Autowired
    TransferEventConsumer transferEventConsumer;

    @Autowired
    LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    LedgerLineRepository ledgerLineRepository;

    @Autowired
    TransferFailureEventRepository transferFailureEventRepository;

    @BeforeEach
    void setUp() {
        transferFailureEventRepository.deleteAll();
        ledgerEntryRepository.deleteAll();
    }

    @Test
    void handleTransferCompletedSkipsDuplicateEventByTransferId() throws Exception {
        String payload = """
                {
                  "transferId": 100,
                  "senderUserId": 1,
                  "receiverUserId": 2,
                  "amount": 3000
                }
                """;

        transferEventConsumer.handleTransferCompleted(payload);
        transferEventConsumer.handleTransferCompleted(payload);

        var entry = ledgerEntryRepository.findByTransferId(100L).orElseThrow();
        assertThat(ledgerEntryRepository.count()).isEqualTo(1);
        assertThat(ledgerLineRepository.countByLedgerEntryTransferId(100L)).isEqualTo(2);
        assertThat(entry.getLines()).extracting("type")
                .containsExactlyInAnyOrder(LedgerLineType.DEBIT, LedgerLineType.CREDIT);
    }

    @Test
    void handleTransferFailedSkipsDuplicateEventByTransferId() throws Exception {
        String payload = """
                {
                  "transferId": 200,
                  "senderUserId": 1,
                  "receiverUserId": 2,
                  "amount": 3000,
                  "status": "FAILED",
                  "failureReason": "wallet timeout"
                }
                """;

        transferEventConsumer.handleTransferFailed(payload);
        transferEventConsumer.handleTransferFailed(payload);

        var failure = transferFailureEventRepository.findByTransferId(200L).orElseThrow();
        assertThat(transferFailureEventRepository.count()).isEqualTo(1);
        assertThat(failure.getStatus()).isEqualTo("FAILED");
        assertThat(failure.getFailureReason()).isEqualTo("wallet timeout");
        assertThat(ledgerEntryRepository.count()).isZero();
    }

    @Test
    void completedAndFailedEventsWithSameTransferIdAreTrackedSeparately() throws Exception {
        String completedPayload = """
                {
                  "transferId": 300,
                  "senderUserId": 1,
                  "receiverUserId": 2,
                  "amount": 3000
                }
                """;
        String failedPayload = """
                {
                  "transferId": 300,
                  "senderUserId": 1,
                  "receiverUserId": 2,
                  "amount": 3000,
                  "status": "COMPENSATION_REQUIRED",
                  "failureReason": "deposit failed"
                }
                """;

        transferEventConsumer.handleTransferCompleted(completedPayload);
        transferEventConsumer.handleTransferFailed(failedPayload);

        assertThat(ledgerEntryRepository.findByTransferId(300L)).isPresent();
        assertThat(transferFailureEventRepository.findByTransferId(300L)).isPresent();
        assertThat(ledgerLineRepository.countByLedgerEntryTransferId(300L)).isEqualTo(2);
    }
}
