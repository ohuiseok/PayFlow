package com.payflow.gateway.auth;

public record AuthenticatedUser(
        Long userId,
        String email,
        String role
) {
}
