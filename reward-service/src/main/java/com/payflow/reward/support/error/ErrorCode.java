package com.payflow.reward.support.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Request value is invalid."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access is forbidden."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource was not found."),
    RESOURCE_OWNER_MISMATCH(HttpStatus.FORBIDDEN, "Resource owner does not match."),

    FAMILY_LINK_NOT_FOUND(HttpStatus.NOT_FOUND, "Family link was not found."),
    DUPLICATE_FAMILY_LINK(HttpStatus.CONFLICT, "Family link already exists."),
    MISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Mission was not found."),
    INVALID_MISSION_STATUS(HttpStatus.CONFLICT, "Mission status is invalid for this operation."),
    REWARD_PAYMENT_FAILED(HttpStatus.CONFLICT, "Reward payment failed."),

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
