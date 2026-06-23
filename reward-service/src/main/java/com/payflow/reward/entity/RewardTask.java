package com.payflow.reward.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "reward_tasks")
public class RewardTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long parentUserId;

    @Column(nullable = false)
    private Long childUserId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal rewardAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RewardTaskStatus status;

    @Column(length = 1000)
    private String submissionNote;

    @Column(length = 500)
    private String rejectReason;

    private Long transferId;

    @Column(length = 500)
    private String failureReason;

    private LocalDate missionDate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected RewardTask() {
    }

    public RewardTask(Long parentUserId, Long childUserId, String title, String description, BigDecimal rewardAmount, LocalDate missionDate) {
        this.parentUserId = parentUserId;
        this.childUserId = childUserId;
        this.title = title;
        this.description = description;
        this.rewardAmount = rewardAmount;
        this.missionDate = missionDate;
        this.status = RewardTaskStatus.CREATED;
    }

    public void submit(String submissionNote) {
        this.status = RewardTaskStatus.SUBMITTED;
        this.submissionNote = submissionNote;
        this.rejectReason = null;
    }

    public void approve() {
        this.status = RewardTaskStatus.APPROVED;
        this.rejectReason = null;
    }

    public void reject(String rejectReason) {
        this.status = RewardTaskStatus.REJECTED;
        this.rejectReason = rejectReason;
    }

    public void cancel() {
        this.status = RewardTaskStatus.CANCELED;
    }

    public void markPaid(Long transferId) {
        this.status = RewardTaskStatus.PAID;
        this.transferId = transferId;
        this.failureReason = null;
    }

    public void recordPaymentFailure(String failureReason) {
        this.failureReason = failureReason;
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

    public Long getParentUserId() {
        return parentUserId;
    }

    public Long getChildUserId() {
        return childUserId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getRewardAmount() {
        return rewardAmount;
    }

    public RewardTaskStatus getStatus() {
        return status;
    }

    public String getSubmissionNote() {
        return submissionNote;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public Long getTransferId() {
        return transferId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public LocalDate getMissionDate() {
        return missionDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
