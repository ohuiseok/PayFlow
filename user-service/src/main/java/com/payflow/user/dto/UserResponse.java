package com.payflow.user.dto;

import com.payflow.user.entity.User;
import com.payflow.user.entity.UserStatus;

public record UserResponse(
        Long userId,
        String email,
        String name,
        UserStatus status
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getStatus()
        );
    }
}
