package com.payflow.ledger.entity;

import com.payflow.ledger.event.TransferFailedEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "transfer_failure_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_transfer_failure_events_transfer_id", columnNames = "transferId")
        }
)
public class TransferFailureEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long transferId;

    @Column(nullable = false)
    private Long senderUserId;

    @Column(nullable = false)
    private Long receiverUserId;

    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal amount;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(length = 500)
    private String failureReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected TransferFailureEvent() {
    }

    public TransferFailureEvent(TransferFailedEvent event) {
        this.transferId = event.transferId();
        this.senderUserId = event.senderUserId();
        this.receiverUserId = event.receiverUserId();
        this.amount = event.amount();
        this.status = event.status();
        this.failureReason = normalizeFailureReason(event.failureReason());
    }

    private String normalizeFailureReason(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getTransferId() {
        return transferId;
    }

    public Long getSenderUserId() {
        return senderUserId;
    }

    public Long getReceiverUserId() {
        return receiverUserId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
