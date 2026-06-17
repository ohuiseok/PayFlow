package com.payflow.reward.dto;

import jakarta.validation.constraints.Size;

public record SubmitMissionRequest(
        @Size(max = 1000) String submissionNote
) {
}
