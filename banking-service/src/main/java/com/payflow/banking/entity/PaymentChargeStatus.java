package com.payflow.banking.entity;

public enum PaymentChargeStatus {
    READY,
    PAYMENT_PENDING,
    PAYMENT_APPROVED,
    WALLET_REFLECTING,
    COMPLETED,
    FAILED,
    CANCELED,
    PARTIAL_CANCELED,
    EXPIRED,
    UNKNOWN,
    COMPENSATION_REQUIRED
}
