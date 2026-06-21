package com.payflow.reward.client;

public record UserResponse(
        Long userId,
        String phoneNumber,
        String name,
        String role,
        String status
) {
}
