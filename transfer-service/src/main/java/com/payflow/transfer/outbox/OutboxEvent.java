package com.payflow.transfer.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "outbox_events",
        indexes = {
                @Index(name = "idx_outbox_events_status", columnList = "status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_outbox_events_event_id", columnNames = "eventId")
        }
)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36)
    private String eventId;

    @Column(nullable = false, length = 120)
    private String topic;

    @Column(nullable = false, length = 120)
    private String eventKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxEventStatus status;

    @Column(nullable = false)
    private int retryCount;

    @Column(length = 500)
    private String lastError;

    private LocalDateTime processingStartedAt;

    private LocalDateTime publishedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(String topic, String eventKey, String payload) {
        this.eventId = UUID.randomUUID().toString();
        this.topic = topic;
        this.eventKey = eventKey;
        this.payload = payload;
        this.status = OutboxEventStatus.PENDING;
        this.retryCount = 0;
    }

    public void markPublished() {
        this.status = OutboxEventStatus.PUBLISHED;
        this.processingStartedAt = null;
        this.publishedAt = LocalDateTime.now();
        this.lastError = null;
    }

    public void markProcessing() {
        markProcessing(LocalDateTime.now());
    }

    void markProcessing(LocalDateTime processingStartedAt) {
        this.status = OutboxEventStatus.PROCESSING;
        this.processingStartedAt = processingStartedAt;
    }

    public void markFailed(String errorMessage) {
        this.status = OutboxEventStatus.FAILED;
        this.processingStartedAt = null;
        this.retryCount++;
        if (errorMessage == null || errorMessage.isBlank()) {
            this.lastError = "Kafka publish failed";
            return;
        }
        this.lastError = errorMessage.length() > 500 ? errorMessage.substring(0, 500) : errorMessage;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getTopic() {
        return topic;
    }

    public String getEventKey() {
        return eventKey;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxEventStatus getStatus() {
        return status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getLastError() {
        return lastError;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getProcessingStartedAt() {
        return processingStartedAt;
    }
}
