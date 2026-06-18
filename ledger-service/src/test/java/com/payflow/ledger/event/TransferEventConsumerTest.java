package com.payflow.ledger.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.payflow.ledger.entity.LedgerLineType;
import com.payflow.ledger.repository.LedgerEntryRepository;
import com.payflow.ledger.repository.LedgerLineRepository;
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

    @BeforeEach
    void setUp() {
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
}
