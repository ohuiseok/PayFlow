package com.payflow.banking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "toss_payment_events",
        indexes = {
                @Index(name = "idx_toss_payment_events_order", columnList = "toss_payment_order_id")
        }
)
public class TossPaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long tossPaymentOrderId;

    @Column(nullable = false, length = 50)
    private String eventType;

    @Column(length = 200)
    private String paymentKey;

    @Column(length = 64)
    private String transactionKey;

    @Column(length = 40)
    private String tossStatus;

    @Column(unique = true, length = 255)
    private String eventIdempotencyKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    protected TossPaymentEvent() {
    }

    public TossPaymentEvent(
            Long tossPaymentOrderId,
            String eventType,
            String paymentKey,
            String transactionKey,
            String tossStatus,
            String eventIdempotencyKey,
            String payloadJson
    ) {
        this.tossPaymentOrderId = tossPaymentOrderId;
        this.eventType = eventType;
        this.paymentKey = paymentKey;
        this.transactionKey = transactionKey;
        this.tossStatus = tossStatus;
        this.eventIdempotencyKey = eventIdempotencyKey;
        this.payloadJson = payloadJson;
    }

    @PrePersist
    void prePersist() {
        this.receivedAt = LocalDateTime.now();
    }
}
