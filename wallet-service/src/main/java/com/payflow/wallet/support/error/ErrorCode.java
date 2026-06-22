package com.payflow.wallet.support.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    RESOURCE_OWNER_MISMATCH(HttpStatus.FORBIDDEN, "리소스 소유자가 일치하지 않습니다."),

    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 사용자입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),

    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "지갑을 찾을 수 없습니다."),
    WALLET_LOCKED(HttpStatus.CONFLICT, "잠긴 지갑입니다."),
    INSUFFICIENT_BALANCE(HttpStatus.CONFLICT, "잔액이 부족합니다."),
    DUPLICATE_WALLET(HttpStatus.CONFLICT, "이미 생성된 지갑이 있습니다."),
    DUPLICATE_WALLET_REFERENCE(HttpStatus.CONFLICT, "이미 처리된 지갑 거래 참조입니다."),

    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "Idempotency-Key 헤더가 필요합니다."),
    IDEMPOTENCY_REQUEST_MISMATCH(HttpStatus.CONFLICT, "같은 Idempotency-Key로 다른 요청을 보낼 수 없습니다."),
    TRANSFER_NOT_FOUND(HttpStatus.NOT_FOUND, "송금을 찾을 수 없습니다."),
    INVALID_TRANSFER_STATUS(HttpStatus.CONFLICT, "송금 상태가 올바르지 않습니다."),
    WALLET_LOCK_CONFLICT(HttpStatus.CONFLICT, "지갑 처리 중입니다. 잠시 후 다시 시도해주세요."),

    OUTBOX_PUBLISH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이벤트 발행에 실패했습니다."),
    LEDGER_DUPLICATE_EVENT(HttpStatus.CONFLICT, "이미 처리된 원장 이벤트입니다."),
    SETTLEMENT_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 완료된 정산입니다."),

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

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
