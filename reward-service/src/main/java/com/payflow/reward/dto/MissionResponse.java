package com.payflow.reward.dto;

import com.payflow.reward.entity.RewardTask;
import com.payflow.reward.entity.RewardTaskStatus;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

public record MissionResponse(
        Long missionId,
        Long parentUserId,
        Long childUserId,
        String childName,
        String title,
        String description,
        BigDecimal rewardAmount,
        RewardTaskStatus status,
        String missionDate,
        String submissionNote,
        String rejectReason,
        Long transferId,
        String failureReason
    ) {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static MissionResponse from(RewardTask task) {
        return from(task, null);
    }

    public static MissionResponse from(RewardTask task, String childName) {
        return new MissionResponse(
                task.getId(),
                task.getParentUserId(),
                task.getChildUserId(),
                childName,
                task.getTitle(),
                task.getDescription(),
                task.getRewardAmount(),
                task.getStatus(),
                task.getMissionDate() != null ? task.getMissionDate().format(DATE_FMT) : null,
                task.getSubmissionNote(),
                task.getRejectReason(),
                task.getTransferId(),
                task.getFailureReason()
        );
    }
}
