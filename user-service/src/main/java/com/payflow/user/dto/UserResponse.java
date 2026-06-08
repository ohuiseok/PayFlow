package com.payflow.user.dto;

import com.payflow.user.entity.User;
import com.payflow.user.entity.UserRole;
import com.payflow.user.entity.UserStatus;

public record UserResponse(
        Long userId,
        String phoneNumber,
        String name,
        UserRole role,
        UserStatus status
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getPhoneNumber(),
                user.getName(),
                user.getRole(),
                user.getStatus()
        );
    }
}
