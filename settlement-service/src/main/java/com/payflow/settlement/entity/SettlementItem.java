package com.payflow.settlement.entity;

import com.payflow.settlement.event.PaymentSettlementEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "settlement_items")
public class SettlementItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long settlementRunId;
    @Column(nullable = false, unique = true, length = 100)
    private String eventId;
    @Column(nullable = false)
    private Long chargeId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private PaymentSettlementEventType transactionType;
    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal expectedAmount;
    @Column(precision = 19, scale = 0)
    private BigDecimal ledgerAmount;
    private Long ledgerEntryId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private ReconciliationStatus status;
    @Column(length = 500)
    private String discrepancyReason;

    protected SettlementItem() {}
    public SettlementItem(Long runId, SettlementTransaction transaction) {
        this.settlementRunId = runId; this.eventId = transaction.getEventId(); this.chargeId = transaction.getChargeId();
        this.transactionType = transaction.getTransactionType(); this.expectedAmount = transaction.getAmount();
    }
    public void apply(Long ledgerEntryId, BigDecimal ledgerAmount) {
        this.ledgerEntryId = ledgerEntryId; this.ledgerAmount = ledgerAmount;
        if (ledgerAmount == null) { status = ReconciliationStatus.MISSING_LEDGER; discrepancyReason = "Ledger entry was not found"; }
        else if (expectedAmount.compareTo(ledgerAmount) != 0) { status = ReconciliationStatus.AMOUNT_MISMATCH; discrepancyReason = "Expected " + expectedAmount + " but ledger was " + ledgerAmount; }
        else { status = ReconciliationStatus.MATCHED; discrepancyReason = null; }
    }
    public ReconciliationStatus getStatus() { return status; }
    public String getEventId() { return eventId; }
}
