package com.payflow.reward.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateMissionRequest(
        @NotNull Long childUserId,
        @NotBlank @Size(max = 120) String title,
        @NotBlank @Size(max = 1000) String description,
        @NotNull BigDecimal rewardAmount,
        LocalDate missionDate
) {
    public CreateMissionRequest(Long childUserId, String title, String description, BigDecimal rewardAmount) {
        this(childUserId, title, description, rewardAmount, null);
    }
}
