package com.payflow.banking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "banking_api_logs")
public class BankingApiLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long bankingTransferId;

    @Column(nullable = false, length = 50)
    private String apiName;

    @Column(length = 100)
    private String requestId;

    private Integer httpStatus;

    @Column(length = 20)
    private String apiResponseCode;

    @Column(length = 80)
    private String apiTranId;

    @Column(length = 20)
    private String bankResponseCode;

    @Column(length = 1000)
    private String requestKeys;

    @Column(length = 1000)
    private String responseKeys;

    @Column(length = 500)
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected BankingApiLog() {
    }

    public BankingApiLog(
            Long bankingTransferId,
            String apiName,
            String requestId,
            Integer httpStatus,
            String apiResponseCode,
            String apiTranId,
            String bankResponseCode,
            String requestKeys,
            String responseKeys,
            String errorMessage
    ) {
        this.bankingTransferId = bankingTransferId;
        this.apiName = apiName;
        this.requestId = requestId;
        this.httpStatus = httpStatus;
        this.apiResponseCode = apiResponseCode;
        this.apiTranId = apiTranId;
        this.bankResponseCode = bankResponseCode;
        this.requestKeys = requestKeys;
        this.responseKeys = responseKeys;
        this.errorMessage = errorMessage;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
