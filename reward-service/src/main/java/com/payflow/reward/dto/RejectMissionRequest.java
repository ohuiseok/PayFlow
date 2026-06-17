package com.payflow.reward.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectMissionRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
