package com.payflow.reward.controller;

import com.payflow.reward.dto.CreateMissionRequest;
import com.payflow.reward.dto.MissionResponse;
import com.payflow.reward.dto.RejectMissionRequest;
import com.payflow.reward.dto.SubmitMissionRequest;
import com.payflow.reward.entity.RewardTaskStatus;
import com.payflow.reward.service.RewardService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/missions")
@RequiredArgsConstructor
public class MissionController {

    private final RewardService rewardService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MissionResponse createMission(
            @Valid @RequestBody CreateMissionRequest request,
            @RequestHeader("X-User-Id") Long requestUserId,
            @RequestHeader("X-User-Role") String role
    ) {
        return rewardService.createMission(request, requestUserId, role);
    }

    @GetMapping
    public List<MissionResponse> getMissions(
            @RequestHeader("X-User-Id") Long requestUserId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(required = false) RewardTaskStatus status,
            @RequestParam(required = false) LocalDate date
    ) {
        return rewardService.getMissions(requestUserId, role, status, date);
    }

    @GetMapping("/{missionId}")
    public MissionResponse getMission(
            @PathVariable Long missionId,
            @RequestHeader("X-User-Id") Long requestUserId,
            @RequestHeader("X-User-Role") String role
    ) {
        return rewardService.getMission(missionId, requestUserId, role);
    }

    @PatchMapping("/{missionId}/submit")
    public MissionResponse submitMission(
            @PathVariable Long missionId,
            @Valid @RequestBody(required = false) SubmitMissionRequest request,
            @RequestHeader("X-User-Id") Long requestUserId,
            @RequestHeader("X-User-Role") String role
    ) {
        SubmitMissionRequest safeRequest = request == null ? new SubmitMissionRequest(null) : request;
        return rewardService.submitMission(missionId, safeRequest, requestUserId, role);
    }

    @PatchMapping("/{missionId}/approve")
    public MissionResponse approveMission(
            @PathVariable Long missionId,
            @RequestHeader("X-User-Id") Long requestUserId,
            @RequestHeader("X-User-Role") String role
    ) {
        return rewardService.approveMission(missionId, requestUserId, role);
    }

    @PatchMapping("/{missionId}/reject")
    public MissionResponse rejectMission(
            @PathVariable Long missionId,
            @Valid @RequestBody RejectMissionRequest request,
            @RequestHeader("X-User-Id") Long requestUserId,
            @RequestHeader("X-User-Role") String role
    ) {
        return rewardService.rejectMission(missionId, request, requestUserId, role);
    }

    @PostMapping("/{missionId}/pay")
    public MissionResponse payMission(
            @PathVariable Long missionId,
            @RequestHeader("X-User-Id") Long requestUserId,
            @RequestHeader("X-User-Role") String role
    ) {
        return rewardService.payMission(missionId, requestUserId, role);
    }
}
