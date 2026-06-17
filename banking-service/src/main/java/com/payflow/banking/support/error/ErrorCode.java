package com.payflow.banking.support.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Request value is invalid."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access is forbidden."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource was not found."),
    RESOURCE_OWNER_MISMATCH(HttpStatus.FORBIDDEN, "Resource owner does not match."),

    BANK_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Bank account was not found."),
    DUPLICATE_BANK_ACCOUNT(HttpStatus.CONFLICT, "Bank account is already registered."),
    BANKING_TRANSFER_NOT_FOUND(HttpStatus.NOT_FOUND, "Banking transfer was not found."),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required."),
    IDEMPOTENCY_REQUEST_MISMATCH(HttpStatus.CONFLICT, "Same Idempotency-Key cannot be used with a different request."),
    WALLET_DEPOSIT_FAILED(HttpStatus.CONFLICT, "Wallet deposit failed."),

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
