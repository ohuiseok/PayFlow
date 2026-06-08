package com.payflow.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.payflow.user.entity.UserRole;

public record CreateUserRequest(
        @NotBlank
        @Pattern(regexp = "\\d{10,11}")
        String phoneNumber,

        @NotBlank
        @Size(min = 8)
        String password,

        @NotBlank
        String name,

        @NotNull
        UserRole role
) {
}
