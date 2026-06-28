package com.payflow.settlement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "settlement_runs")
public class SettlementRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private LocalDate businessDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private SettlementRunStatus status;
    @Column(nullable = false)
    private long transactionCount;
    @Column(nullable = false)
    private long discrepancyCount;
    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal grossAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal cancelAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal feeAmount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal expectedNetAmount = BigDecimal.ZERO;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    @Column(length = 500)
    private String failureReason;

    protected SettlementRun() {}
    public SettlementRun(LocalDate businessDate) { this.businessDate = businessDate; start(); }
    public void start() { status = SettlementRunStatus.RUNNING; startedAt = LocalDateTime.now(); completedAt = null; failureReason = null; }
    public void complete(long count, long discrepancies, BigDecimal gross, BigDecimal cancel, BigDecimal fee) {
        transactionCount = count; discrepancyCount = discrepancies; grossAmount = gross; cancelAmount = cancel;
        feeAmount = fee; expectedNetAmount = gross.subtract(cancel).subtract(fee);
        status = discrepancies == 0 ? SettlementRunStatus.COMPLETED : SettlementRunStatus.WITH_DISCREPANCY;
        completedAt = LocalDateTime.now();
    }
    public void fail(String reason) { status = SettlementRunStatus.FAILED; failureReason = reason; completedAt = LocalDateTime.now(); }
    public Long getId() { return id; }
    public LocalDate getBusinessDate() { return businessDate; }
    public SettlementRunStatus getStatus() { return status; }
    public long getTransactionCount() { return transactionCount; }
    public long getDiscrepancyCount() { return discrepancyCount; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public BigDecimal getCancelAmount() { return cancelAmount; }
    public BigDecimal getFeeAmount() { return feeAmount; }
    public BigDecimal getExpectedNetAmount() { return expectedNetAmount; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public String getFailureReason() { return failureReason; }
}
