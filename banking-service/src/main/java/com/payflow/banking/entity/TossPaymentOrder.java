package com.payflow.banking.entity;

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
import java.time.LocalDateTime;

@Entity
@Table(name = "toss_payment_orders")
public class TossPaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long paymentChargeId;

    @Column(nullable = false, unique = true, length = 64)
    private String tossOrderId;

    @Column(unique = true, length = 200)
    private String paymentKey;

    @Column(nullable = false, length = 100)
    private String orderName;

    @Column(length = 50)
    private String method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TossPaymentStatus tossStatus;

    @Column(nullable = false, precision = 19, scale = 0)
    private BigDecimal totalAmount;

    @Column(precision = 19, scale = 0)
    private BigDecimal balanceAmount;

    private LocalDateTime approvedAt;

    @Column(length = 500)
    private String receiptUrl;

    @Column(length = 500)
    private String checkoutUrl;

    @Column(columnDefinition = "TEXT")
    private String rawResponseJson;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected TossPaymentOrder() {
    }

    public TossPaymentOrder(Long paymentChargeId, String tossOrderId, String orderName, BigDecimal totalAmount) {
        this.paymentChargeId = paymentChargeId;
        this.tossOrderId = tossOrderId;
        this.orderName = orderName;
        this.totalAmount = totalAmount;
        this.tossStatus = TossPaymentStatus.READY;
    }

    public void applyPaymentResult(
            String paymentKey,
            String method,
            TossPaymentStatus tossStatus,
            BigDecimal totalAmount,
            BigDecimal balanceAmount,
            LocalDateTime approvedAt,
            String receiptUrl,
            String checkoutUrl,
            String rawResponseJson
    ) {
        this.paymentKey = paymentKey;
        this.method = method;
        this.tossStatus = tossStatus == null ? TossPaymentStatus.UNKNOWN : tossStatus;
        this.totalAmount = totalAmount == null ? this.totalAmount : totalAmount;
        this.balanceAmount = balanceAmount;
        this.approvedAt = approvedAt;
        this.receiptUrl = receiptUrl;
        this.checkoutUrl = checkoutUrl;
        this.rawResponseJson = rawResponseJson;
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

    public Long getPaymentChargeId() {
        return paymentChargeId;
    }

    public String getTossOrderId() {
        return tossOrderId;
    }

    public String getPaymentKey() {
        return paymentKey;
    }

    public String getOrderName() {
        return orderName;
    }

    public TossPaymentStatus getTossStatus() {
        return tossStatus;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getBalanceAmount() {
        return balanceAmount;
    }

    public String getReceiptUrl() {
        return receiptUrl;
    }
}
