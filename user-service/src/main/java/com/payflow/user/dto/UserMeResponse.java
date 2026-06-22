package com.payflow.user.dto;

import com.payflow.user.entity.User;
import com.payflow.user.entity.UserRole;
import com.payflow.user.entity.UserStatus;

public record UserMeResponse(
        Long userId,
        String phoneNumber,
        String name,
        UserRole role,
        UserStatus status,
        boolean hasBankAccount
) {

    public static UserMeResponse from(User user, boolean hasBankAccount) {
        return new UserMeResponse(
                user.getId(),
                user.getPhoneNumber(),
                user.getName(),
                user.getRole(),
                user.getStatus(),
                hasBankAccount
        );
    }
}
