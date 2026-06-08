package com.payflow.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(
        @NotBlank
        @Pattern(regexp = "\\d{10,11}")
        String phoneNumber,

        @NotBlank
        String password
) {
}
