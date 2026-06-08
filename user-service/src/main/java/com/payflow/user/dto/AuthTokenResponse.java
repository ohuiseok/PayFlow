package com.payflow.user.dto;

public record AuthTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
}
