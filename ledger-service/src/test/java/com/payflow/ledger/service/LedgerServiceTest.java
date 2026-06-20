package com.payflow.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.payflow.ledger.dto.PaymentLedgerRequest;
import com.payflow.ledger.entity.LedgerEntryType;
import com.payflow.ledger.entity.LedgerLineType;
import com.payflow.ledger.entity.LedgerSourceType;
import com.payflow.ledger.event.TransferCompletedEvent;
import com.payflow.ledger.event.TransferFailedEvent;
import com.payflow.ledger.repository.LedgerEntryRepository;
import com.payflow.ledger.repository.LedgerLineRepository;
import com.payflow.ledger.repository.TransferFailureEventRepository;
import com.payflow.ledger.support.error.BusinessException;
import com.payflow.ledger.support.error.ErrorCode;
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
    void recordPaymentChargeCreatesDoubleEntryLinesOnce() {
        var request = new PaymentLedgerRequest(
                LedgerSourceType.TOSS_CHARGE,
                300L,
                LedgerEntryType.USER_WALLET_TOPUP,
                1L,
                new BigDecimal("10000")
        );

        ledgerService.recordPaymentCharge(request);
        var entry = ledgerService.recordPaymentCharge(request);

        assertThat(ledgerEntryRepository.count()).isEqualTo(1);
        assertThat(ledgerLineRepository.countByLedgerEntry_SourceTypeAndLedgerEntry_SourceId(
                LedgerSourceType.TOSS_CHARGE,
                300L
        )).isEqualTo(2);
        assertThat(entry.sourceType()).isEqualTo(LedgerSourceType.TOSS_CHARGE);
        assertThat(entry.entryType()).isEqualTo(LedgerEntryType.USER_WALLET_TOPUP);
        assertThat(entry.lines()).hasSize(2);
        assertThat(entry.lines()).extracting("accountCode")
                .containsExactlyInAnyOrder("PG_CASH", "USER_WALLET");
        assertThat(entry.lines()).extracting("type")
                .containsExactlyInAnyOrder(LedgerLineType.DEBIT, LedgerLineType.CREDIT);
    }

    @Test
    void recordPaymentCancelCreatesDoubleEntryLinesOnce() {
        var request = new PaymentLedgerRequest(
                LedgerSourceType.TOSS_CANCEL,
                301L,
                LedgerEntryType.PG_CANCEL,
                1L,
                new BigDecimal("5000")
        );

        ledgerService.recordPaymentCharge(request);
        var entry = ledgerService.recordPaymentCharge(request);

        assertThat(ledgerEntryRepository.count()).isEqualTo(1);
        assertThat(entry.sourceType()).isEqualTo(LedgerSourceType.TOSS_CANCEL);
        assertThat(entry.entryType()).isEqualTo(LedgerEntryType.PG_CANCEL);
        assertThat(entry.lines()).extracting("accountCode")
                .containsExactlyInAnyOrder("USER_WALLET", "PG_CASH");
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

    @Test
    void getTransferFailureReturnsFailureEvent() {
        ledgerService.recordTransferFailure(new TransferFailedEvent(200L, 1L, 2L, new BigDecimal("3000"), "FAILED", "wallet timeout"));

        var response = ledgerService.getTransferFailure(200L);

        assertThat(response.transferId()).isEqualTo(200L);
        assertThat(response.senderUserId()).isEqualTo(1L);
        assertThat(response.receiverUserId()).isEqualTo(2L);
        assertThat(response.amount()).isEqualByComparingTo("3000");
        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.failureReason()).isEqualTo("wallet timeout");
        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    void getTransferFailureThrowsWhenNotFound() {
        assertThatThrownBy(() -> ledgerService.getTransferFailure(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }
}
