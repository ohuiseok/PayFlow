package com.payflow.transfer.entity;

public enum TransferStatus {
    REQUESTED,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    COMPENSATION_REQUIRED,
    COMPENSATED
}
