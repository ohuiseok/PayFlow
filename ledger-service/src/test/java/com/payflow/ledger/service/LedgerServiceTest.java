package com.payflow.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.payflow.ledger.entity.LedgerLineType;
import com.payflow.ledger.event.TransferCompletedEvent;
import com.payflow.ledger.repository.LedgerEntryRepository;
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

    @BeforeEach
    void setUp() {
        ledgerEntryRepository.deleteAll();
    }

    @Test
    void recordTransferCreatesDoubleEntryLinesOnce() {
        var event = new TransferCompletedEvent(100L, 1L, 2L, new BigDecimal("3000"));

        ledgerService.recordTransfer(event);
        var entry = ledgerService.recordTransfer(event);

        assertThat(ledgerEntryRepository.count()).isEqualTo(1);
        assertThat(entry.getTransferId()).isEqualTo(100L);
        assertThat(entry.getLines()).hasSize(2);
        assertThat(entry.getLines()).extracting("type")
                .containsExactlyInAnyOrder(LedgerLineType.DEBIT, LedgerLineType.CREDIT);
    }
}
