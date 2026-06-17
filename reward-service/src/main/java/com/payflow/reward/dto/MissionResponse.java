package com.payflow.reward.dto;

import com.payflow.reward.entity.RewardTask;
import com.payflow.reward.entity.RewardTaskStatus;
import java.math.BigDecimal;

public record MissionResponse(
        Long missionId,
        Long parentUserId,
        Long childUserId,
        String title,
        String description,
        BigDecimal rewardAmount,
        RewardTaskStatus status,
        String submissionNote,
        String rejectReason,
        Long transferId,
        String failureReason
) {

    public static MissionResponse from(RewardTask task) {
        return new MissionResponse(
                task.getId(),
                task.getParentUserId(),
                task.getChildUserId(),
                task.getTitle(),
                task.getDescription(),
                task.getRewardAmount(),
                task.getStatus(),
                task.getSubmissionNote(),
                task.getRejectReason(),
                task.getTransferId(),
                task.getFailureReason()
        );
    }
}
