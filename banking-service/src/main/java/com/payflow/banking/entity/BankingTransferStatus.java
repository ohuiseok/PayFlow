package com.payflow.banking.entity;

public enum BankingTransferStatus {
    REQUESTED,
    BANK_PROCESSING,
    BANK_SUCCEEDED,
    WALLET_REFLECTING,
    COMPLETED,
    SUCCEEDED,
    UNKNOWN,
    COMPENSATION_REQUIRED,
    FAILED
}
