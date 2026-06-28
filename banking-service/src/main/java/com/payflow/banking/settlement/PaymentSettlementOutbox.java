package com.payflow.banking.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_settlement_outbox")
public class PaymentSettlementOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String eventId;

    @Column(nullable = false, length = 120)
    private String topic;

    @Column(nullable = false, length = 120)
    private String eventKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SettlementOutboxStatus status;

    @Column(nullable = false)
    private int retryCount;

    @Column(length = 500)
    private String lastError;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime publishedAt;

    protected PaymentSettlementOutbox() {
    }

    public PaymentSettlementOutbox(String eventId, String topic, String eventKey, String payload) {
        this.eventId = eventId;
        this.topic = topic;
        this.eventKey = eventKey;
        this.payload = payload;
        this.status = SettlementOutboxStatus.PENDING;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public void markPublished() {
        this.status = SettlementOutboxStatus.PUBLISHED;
        this.lastError = null;
        this.publishedAt = LocalDateTime.now();
    }

    public void markFailed(String error) {
        this.status = SettlementOutboxStatus.FAILED;
        this.retryCount += 1;
        this.lastError = error;
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getTopic() { return topic; }
    public String getEventKey() { return eventKey; }
    public String getPayload() { return payload; }
    public SettlementOutboxStatus getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
}
