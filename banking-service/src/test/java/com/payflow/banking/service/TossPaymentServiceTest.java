package com.payflow.banking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.doThrow;

import com.payflow.banking.client.LedgerClient;
import com.payflow.banking.client.LedgerPaymentRecordRequest;
import com.payflow.banking.client.WalletBalanceChangeRequest;
import com.payflow.banking.client.WalletClient;
import com.payflow.banking.client.WalletResponse;
import com.payflow.banking.dto.CreateTossChargeRequest;
import com.payflow.banking.dto.TossConfirmRequest;
import com.payflow.banking.dto.TossPaymentWebhookRequest;
import com.payflow.banking.entity.TossPaymentStatus;
import com.payflow.banking.repository.PaymentChargeRepository;
import com.payflow.banking.repository.TossPaymentEventRepository;
import com.payflow.banking.repository.TossPaymentOrderRepository;
import com.payflow.banking.settlement.PaymentSettlementOutboxRepository;
import com.payflow.banking.support.error.BusinessException;
import com.payflow.banking.support.error.ErrorCode;
import com.payflow.banking.toss.TossPaymentResult;
import com.payflow.banking.toss.TossPaymentsClient;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class TossPaymentServiceTest {

    @Autowired
    TossPaymentService tossPaymentService;

    @Autowired
    PaymentChargeRepository paymentChargeRepository;

    @Autowired
    TossPaymentOrderRepository tossPaymentOrderRepository;

    @Autowired
    TossPaymentEventRepository tossPaymentEventRepository;

    @Autowired
    PaymentSettlementOutboxRepository paymentSettlementOutboxRepository;

    @MockitoBean
    TossPaymentsClient tossPaymentsClient;

    @MockitoBean
    WalletClient walletClient;

    @MockitoBean
    LedgerClient ledgerClient;

    @BeforeEach
    void setUp() {
        paymentSettlementOutboxRepository.deleteAll();
        tossPaymentEventRepository.deleteAll();
        tossPaymentOrderRepository.deleteAll();
        paymentChargeRepository.deleteAll();
        when(walletClient.getWalletByUserId(eq(1L), eq(true), any()))
                .thenReturn(new WalletResponse(10L, 1L, new BigDecimal("50000"), "ACTIVE"));
        when(walletClient.deposit(eq(10L), any(WalletBalanceChangeRequest.class), eq(true), any()))
                .thenReturn(new WalletResponse(10L, 1L, new BigDecimal("60000"), "ACTIVE"));
    }

    @Test
    void createTossChargeIsIdempotent() {
        var request = new CreateTossChargeRequest(new BigDecimal("10000"), "PayFlow charge");

        var first = tossPaymentService.createCharge(request, "toss-key-1", 1L);
        var second = tossPaymentService.createCharge(request, "toss-key-1", 1L);

        assertThat(second.chargeId()).isEqualTo(first.chargeId());
        assertThat(paymentChargeRepository.count()).isEqualTo(1);
        assertThat(tossPaymentOrderRepository.count()).isEqualTo(1);
    }

    @Test
    void createTossChargeWithSameIdempotencyKeyAndDifferentRequestFails() {
        tossPaymentService.createCharge(new CreateTossChargeRequest(new BigDecimal("10000"), "A"), "toss-key-2", 1L);

        assertThatThrownBy(() -> tossPaymentService.createCharge(
                new CreateTossChargeRequest(new BigDecimal("20000"), "A"),
                "toss-key-2",
                1L
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IDEMPOTENCY_REQUEST_MISMATCH);
    }

    @Test
    void confirmTossPaymentDepositsToWalletOnce() {
        var create = tossPaymentService.createCharge(
                new CreateTossChargeRequest(new BigDecimal("10000"), "PayFlow charge"),
                "toss-key-3",
                1L
        );
        when(tossPaymentsClient.confirm(eq("payment-key-1"), eq(create.orderId()), eq(new BigDecimal("10000"))))
                .thenReturn(donePayment("payment-key-1", create.orderId(), new BigDecimal("10000")));

        var first = tossPaymentService.confirm(new TossConfirmRequest("payment-key-1", create.orderId(), new BigDecimal("10000")), 1L);
        var second = tossPaymentService.confirm(new TossConfirmRequest("payment-key-1", create.orderId(), new BigDecimal("10000")), 1L);

        assertThat(first.status()).isEqualTo("COMPLETED");
        assertThat(second.status()).isEqualTo("COMPLETED");
        verify(walletClient, times(1)).deposit(eq(10L), any(WalletBalanceChangeRequest.class), eq(true), any());
        verify(ledgerClient, times(1)).recordPaymentCharge(any(LedgerPaymentRecordRequest.class), eq(true), any());
        assertThat(paymentSettlementOutboxRepository.count()).isEqualTo(1);
    }

    @Test
    void duplicatedWebhookIsStoredOnce() {
        var create = tossPaymentService.createCharge(
                new CreateTossChargeRequest(new BigDecimal("10000"), "PayFlow charge"),
                "toss-key-4",
                1L
        );
        var webhook = new TossPaymentWebhookRequest(
                "PAYMENT_STATUS_CHANGED",
                "payment-key-2",
                create.orderId(),
                "transaction-key-1",
                "DONE",
                Map.of("status", "DONE")
        );

        var first = tossPaymentService.receiveWebhook(webhook, null);
        var second = tossPaymentService.receiveWebhook(webhook, null);

        assertThat(first.duplicate()).isFalse();
        assertThat(second.duplicate()).isTrue();
        assertThat(tossPaymentEventRepository.count()).isEqualTo(1);
    }

    @Test
    void retryCompensationDepositsAgainAndCompletesCharge() {
        var create = tossPaymentService.createCharge(
                new CreateTossChargeRequest(new BigDecimal("10000"), "PayFlow charge"),
                "toss-key-5",
                1L
        );
        when(tossPaymentsClient.confirm(eq("payment-key-3"), eq(create.orderId()), eq(new BigDecimal("10000"))))
                .thenReturn(donePayment("payment-key-3", create.orderId(), new BigDecimal("10000")));
        reset(walletClient);
        when(walletClient.getWalletByUserId(eq(1L), eq(true), any()))
                .thenReturn(new WalletResponse(10L, 1L, new BigDecimal("50000"), "ACTIVE"));
        when(walletClient.deposit(eq(10L), any(WalletBalanceChangeRequest.class), eq(true), any()))
                .thenThrow(new RuntimeException("wallet unavailable"))
                .thenReturn(new WalletResponse(10L, 1L, new BigDecimal("60000"), "ACTIVE"));

        var failed = tossPaymentService.confirm(new TossConfirmRequest("payment-key-3", create.orderId(), new BigDecimal("10000")), 1L);
        var recovered = tossPaymentService.retryCompensation(failed.chargeId(), 1L);

        assertThat(failed.status()).isEqualTo("COMPENSATION_REQUIRED");
        assertThat(recovered.status()).isEqualTo("COMPLETED");
        verify(walletClient, times(2)).deposit(eq(10L), any(WalletBalanceChangeRequest.class), eq(true), any());
        verify(ledgerClient, times(1)).recordPaymentCharge(any(LedgerPaymentRecordRequest.class), eq(true), any());
    }

    @Test
    void ledgerFailureAfterWalletDepositIsTrackedAndRetried() {
        var create = tossPaymentService.createCharge(
                new CreateTossChargeRequest(new BigDecimal("10000"), "PayFlow charge"),
                "toss-key-6",
                1L
        );
        when(tossPaymentsClient.confirm(eq("payment-key-4"), eq(create.orderId()), eq(new BigDecimal("10000"))))
                .thenReturn(donePayment("payment-key-4", create.orderId(), new BigDecimal("10000")));
        doThrow(new RuntimeException("ledger unavailable"))
                .doNothing()
                .when(ledgerClient)
                .recordPaymentCharge(any(LedgerPaymentRecordRequest.class), eq(true), any());

        var completedWithLedgerFailure = tossPaymentService.confirm(
                new TossConfirmRequest("payment-key-4", create.orderId(), new BigDecimal("10000")),
                1L
        );
        var ledgerCompensations = tossPaymentService.getLedgerCompensationRequiredCharges();
        var recovered = tossPaymentService.retryLedgerCompensation(completedWithLedgerFailure.chargeId(), 1L);

        assertThat(completedWithLedgerFailure.status()).isEqualTo("COMPLETED");
        assertThat(completedWithLedgerFailure.ledgerRecorded()).isFalse();
        assertThat(completedWithLedgerFailure.ledgerFailureReason()).isEqualTo("ledger unavailable");
        assertThat(ledgerCompensations).hasSize(1);
        assertThat(recovered.ledgerRecorded()).isTrue();
        assertThat(recovered.ledgerFailureReason()).isNull();
        verify(walletClient, times(1)).deposit(eq(10L), any(WalletBalanceChangeRequest.class), eq(true), any());
        verify(ledgerClient, times(2)).recordPaymentCharge(any(LedgerPaymentRecordRequest.class), eq(true), any());
    }

    private TossPaymentResult donePayment(String paymentKey, String orderId, BigDecimal amount) {
        return new TossPaymentResult(
                paymentKey,
                orderId,
                "PayFlow charge",
                "간편결제",
                TossPaymentStatus.DONE,
                amount,
                amount,
                LocalDateTime.now(),
                "https://receipt.example/" + paymentKey,
                null,
                "{\"status\":\"DONE\"}"
        );
    }
}
