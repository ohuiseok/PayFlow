package com.payflow.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.payflow.ledger.entity.LedgerLineType;
import com.payflow.ledger.event.TransferCompletedEvent;
import com.payflow.ledger.event.TransferFailedEvent;
import com.payflow.ledger.repository.LedgerEntryRepository;
import com.payflow.ledger.repository.LedgerLineRepository;
import com.payflow.ledger.repository.TransferFailureEventRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LedgerServiceTest {

    @Autowired
    LedgerService ledgerService;

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
    void recordTransferCreatesDoubleEntryLinesOnce() {
        var event = new TransferCompletedEvent(100L, 1L, 2L, new BigDecimal("3000"));

        ledgerService.recordTransfer(event);
        var entry = ledgerService.recordTransfer(event);

        assertThat(ledgerEntryRepository.count()).isEqualTo(1);
        assertThat(ledgerLineRepository.countByLedgerEntryTransferId(100L)).isEqualTo(2);
        assertThat(entry.getTransferId()).isEqualTo(100L);
        assertThat(entry.getLines()).hasSize(2);
        assertThat(entry.getLines()).extracting("type")
                .containsExactlyInAnyOrder(LedgerLineType.DEBIT, LedgerLineType.CREDIT);
    }

    @Test
    void recordTransferFailureCreatesFailureEventOnce() {
        var event = new TransferFailedEvent(200L, 1L, 2L, new BigDecimal("3000"), "FAILED", "wallet timeout");

        ledgerService.recordTransferFailure(event);
        var failure = ledgerService.recordTransferFailure(event);

        assertThat(transferFailureEventRepository.count()).isEqualTo(1);
        assertThat(failure.getTransferId()).isEqualTo(200L);
        assertThat(failure.getStatus()).isEqualTo("FAILED");
        assertThat(failure.getFailureReason()).isEqualTo("wallet timeout");
    }
}
