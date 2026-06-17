package com.payflow.reward.controller;

import com.payflow.reward.dto.CashbookSummaryResponse;
import com.payflow.reward.dto.MissionResponse;
import com.payflow.reward.service.RewardService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cashbook")
@RequiredArgsConstructor
public class CashbookController {

    private final RewardService rewardService;

    @GetMapping("/children/{childUserId}/summary")
    public CashbookSummaryResponse getSummary(
            @PathVariable Long childUserId,
            @RequestHeader("X-User-Id") Long requestUserId,
            @RequestHeader("X-User-Role") String role
    ) {
        return rewardService.getCashbookSummary(childUserId, requestUserId, role);
    }

    @GetMapping("/children/{childUserId}/entries")
    public List<MissionResponse> getEntries(
            @PathVariable Long childUserId,
            @RequestHeader("X-User-Id") Long requestUserId,
            @RequestHeader("X-User-Role") String role
    ) {
        return rewardService.getCashbookEntries(childUserId, requestUserId, role);
    }
}
