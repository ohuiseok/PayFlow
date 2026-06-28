package com.payflow.settlement.entity;

import com.payflow.settlement.event.PaymentSettlementEvent;
import com.payflow.settlement.event.PaymentSettlementEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "settlement_transactions")
public class SettlementTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 100)
    private String eventId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private PaymentSettlementEventType transactionType;
    @Column(nullable = false)
    private Long chargeId;
    @Column(nullable = false)
    private Long userId;
    @Column(length = 200)
    private String paymentKey;
    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal amount;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(nullable = false, length = 40)
    private String ledgerSourceType;
    @Column(nullable = false)
    private LocalDateTime occurredAt;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected SettlementTransaction() {}

    public SettlementTransaction(PaymentSettlementEvent event) {
        this.eventId = event.eventId();
        this.transactionType = event.type();
        this.chargeId = event.chargeId();
        this.userId = event.userId();
        this.paymentKey = event.paymentKey();
        this.amount = event.amount();
        this.currency = event.currency();
        this.ledgerSourceType = event.ledgerSourceType();
        this.occurredAt = event.occurredAt();
    }

    @PrePersist void prePersist() { createdAt = LocalDateTime.now(); }
    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public PaymentSettlementEventType getTransactionType() { return transactionType; }
    public Long getChargeId() { return chargeId; }
    public Long getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public String getLedgerSourceType() { return ledgerSourceType; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
}
