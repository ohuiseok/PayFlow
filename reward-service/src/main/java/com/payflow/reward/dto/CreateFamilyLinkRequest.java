package com.payflow.reward.dto;

import jakarta.validation.constraints.NotNull;

public record CreateFamilyLinkRequest(
        @NotNull Long childUserId
) {
}
