package com.payflow.reward.controller;

import com.payflow.reward.dto.CreateFamilyLinkRequest;
import com.payflow.reward.dto.FamilyLinkResponse;
import com.payflow.reward.service.RewardService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/families")
@RequiredArgsConstructor
public class FamilyController {

    private final RewardService rewardService;

    @PostMapping("/links")
    @ResponseStatus(HttpStatus.CREATED)
    public FamilyLinkResponse createFamilyLink(
            @Valid @RequestBody CreateFamilyLinkRequest request,
            @RequestHeader("X-User-Id") Long requestUserId,
            @RequestHeader("X-User-Role") String role
    ) {
        return rewardService.createFamilyLink(request, requestUserId, role);
    }

    @GetMapping("/children")
    public List<FamilyLinkResponse> getChildren(
            @RequestHeader("X-User-Id") Long requestUserId,
            @RequestHeader("X-User-Role") String role
    ) {
        return rewardService.getChildren(requestUserId, role);
    }
}
