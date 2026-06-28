package com.payflow.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.settlement.client.LedgerEntrySnapshot;
import com.payflow.settlement.client.LedgerReconciliationClient;
import com.payflow.settlement.entity.SettlementRunStatus;
import com.payflow.settlement.event.PaymentSettlementEvent;
import com.payflow.settlement.event.PaymentSettlementEventConsumer;
import com.payflow.settlement.event.PaymentSettlementEventType;
import com.payflow.settlement.repository.SettlementItemRepository;
import com.payflow.settlement.repository.SettlementRunRepository;
import com.payflow.settlement.repository.SettlementTransactionRepository;
import com.payflow.settlement.service.DailySettlementJobService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class DailyPaymentSettlementJobTest {
    @Autowired PaymentSettlementEventConsumer consumer;
    @Autowired ObjectMapper objectMapper;
    @Autowired DailySettlementJobService jobService;
    @Autowired SettlementTransactionRepository transactionRepository;
    @Autowired SettlementRunRepository runRepository;
    @Autowired SettlementItemRepository itemRepository;
    @MockitoBean LedgerReconciliationClient ledgerClient;

    @BeforeEach
    void clean() {
        itemRepository.deleteAll();
        runRepository.deleteAll();
        transactionRepository.deleteAll();
    }

    @Test
    void collectsKafkaEventsAndReconcilesDailyPgSettlement() throws Exception {
        LocalDate businessDate = LocalDate.of(2099, 1, 15);
        PaymentSettlementEvent charge = event("TOSS_CHARGE:1", PaymentSettlementEventType.CHARGE, 1L,
                new BigDecimal("10000"), "TOSS_CHARGE", businessDate.atTime(10, 0));
        PaymentSettlementEvent cancel = event("TOSS_CANCEL:tx-1", PaymentSettlementEventType.CANCEL, 1L,
                new BigDecimal("1000"), "TOSS_CANCEL", businessDate.atTime(11, 0));

        consumer.consume(objectMapper.writeValueAsString(charge));
        consumer.consume(objectMapper.writeValueAsString(charge));
        consumer.consume(objectMapper.writeValueAsString(cancel));
        when(ledgerClient.findPaymentEntry("TOSS_CHARGE", 1L))
                .thenReturn(Optional.of(new LedgerEntrySnapshot(10L, new BigDecimal("10000"))));
        when(ledgerClient.findPaymentEntry("TOSS_CANCEL", 1L))
                .thenReturn(Optional.of(new LedgerEntrySnapshot(11L, new BigDecimal("1000"))));

        var response = jobService.run(businessDate);
        var repeated = jobService.run(businessDate);

        assertThat(transactionRepository.count()).isEqualTo(2);
        assertThat(response.status()).isEqualTo(SettlementRunStatus.COMPLETED);
        assertThat(response.transactionCount()).isEqualTo(2);
        assertThat(response.discrepancyCount()).isZero();
        assertThat(response.grossAmount()).isEqualByComparingTo("10000");
        assertThat(response.cancelAmount()).isEqualByComparingTo("1000");
        assertThat(response.feeAmount()).isEqualByComparingTo("270");
        assertThat(response.expectedNetAmount()).isEqualByComparingTo("8730");
        assertThat(itemRepository.count()).isEqualTo(2);
        assertThat(repeated.id()).isEqualTo(response.id());
    }

    @Test
    void marksSettlementAsDiscrepancyWhenLedgerEntryIsMissing() throws Exception {
        LocalDate businessDate = LocalDate.of(2099, 1, 16);
        PaymentSettlementEvent charge = event("TOSS_CHARGE:2", PaymentSettlementEventType.CHARGE, 2L,
                new BigDecimal("5000"), "TOSS_CHARGE", businessDate.atTime(9, 0));
        consumer.consume(objectMapper.writeValueAsString(charge));
        when(ledgerClient.findPaymentEntry("TOSS_CHARGE", 2L)).thenReturn(Optional.empty());

        var response = jobService.run(businessDate);

        assertThat(response.status()).isEqualTo(SettlementRunStatus.WITH_DISCREPANCY);
        assertThat(response.discrepancyCount()).isEqualTo(1);
    }

    private PaymentSettlementEvent event(
            String eventId, PaymentSettlementEventType type, Long chargeId, BigDecimal amount,
            String ledgerSourceType, LocalDateTime occurredAt
    ) {
        return new PaymentSettlementEvent(eventId, type, chargeId, 7L, "payment-key", amount,
                "KRW", ledgerSourceType, occurredAt);
    }
}
