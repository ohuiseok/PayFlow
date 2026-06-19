package com.payflow.reward.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.payflow.reward.client.CreateTransferRequest;
import com.payflow.reward.client.TransferClient;
import com.payflow.reward.client.TransferResponse;
import com.payflow.reward.client.WalletClient;
import com.payflow.reward.client.WalletResponse;
import com.payflow.reward.dto.CreateFamilyLinkRequest;
import com.payflow.reward.dto.CreateMissionRequest;
import com.payflow.reward.dto.RejectMissionRequest;
import com.payflow.reward.dto.SubmitMissionRequest;
import com.payflow.reward.entity.RewardTaskStatus;
import com.payflow.reward.repository.ParentChildLinkRepository;
import com.payflow.reward.repository.RewardTaskRepository;
import com.payflow.reward.support.error.BusinessException;
import com.payflow.reward.support.error.ErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class RewardServiceTest {

    @Autowired
    RewardService rewardService;

    @Autowired
    ParentChildLinkRepository parentChildLinkRepository;

    @Autowired
    RewardTaskRepository rewardTaskRepository;

    @MockitoBean
    TransferClient transferClient;

    @MockitoBean
    WalletClient walletClient;

    @BeforeEach
    void setUp() {
        rewardTaskRepository.deleteAll();
        parentChildLinkRepository.deleteAll();
        when(transferClient.createTransfer(any(CreateTransferRequest.class), anyString(), eq(1L), anyString()))
                .thenReturn(new TransferResponse(100L, 1L, 2L, new BigDecimal("3000"), "SUCCEEDED", null));
        when(walletClient.getWalletByUserId(eq(1L), eq(true), any()))
                .thenReturn(new WalletResponse(10L, 1L, new BigDecimal("50000"), "ACTIVE"));
        when(walletClient.getWalletByUserId(eq(2L), eq(true), any()))
                .thenReturn(new WalletResponse(20L, 2L, new BigDecimal("3000"), "ACTIVE"));
    }

    @Test
    void parentCreatesFamilyLinkAndMission() {
        rewardService.createFamilyLink(new CreateFamilyLinkRequest(2L), 1L, "PARENT");

        var response = rewardService.createMission(
                new CreateMissionRequest(2L, "Read", "Read for 30 minutes", new BigDecimal("3000")),
                1L,
                "PARENT"
        );

        assertThat(response.parentUserId()).isEqualTo(1L);
        assertThat(response.childUserId()).isEqualTo(2L);
        assertThat(response.status()).isEqualTo(RewardTaskStatus.CREATED);
    }

    @Test
    void duplicateFamilyLinkFails() {
        rewardService.createFamilyLink(new CreateFamilyLinkRequest(2L), 1L, "PARENT");

        assertThatThrownBy(() -> rewardService.createFamilyLink(new CreateFamilyLinkRequest(2L), 1L, "PARENT"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_FAMILY_LINK);
    }

    @Test
    void childSubmitsAndParentApprovesAndPaysMission() {
        rewardService.createFamilyLink(new CreateFamilyLinkRequest(2L), 1L, "PARENT");
        var mission = rewardService.createMission(
                new CreateMissionRequest(2L, "Read", "Read for 30 minutes", new BigDecimal("3000")),
                1L,
                "PARENT"
        );

        var submitted = rewardService.submitMission(mission.missionId(), new SubmitMissionRequest("done"), 2L, "CHILD");
        var approved = rewardService.approveMission(mission.missionId(), 1L, "PARENT");
        var paid = rewardService.payMission(mission.missionId(), 1L, "PARENT");

        assertThat(submitted.status()).isEqualTo(RewardTaskStatus.SUBMITTED);
        assertThat(approved.status()).isEqualTo(RewardTaskStatus.APPROVED);
        assertThat(paid.status()).isEqualTo(RewardTaskStatus.PAID);
        assertThat(paid.transferId()).isEqualTo(100L);
    }

    @Test
    void parentRejectsSubmittedMission() {
        rewardService.createFamilyLink(new CreateFamilyLinkRequest(2L), 1L, "PARENT");
        var mission = rewardService.createMission(
                new CreateMissionRequest(2L, "Read", "Read for 30 minutes", new BigDecimal("3000")),
                1L,
                "PARENT"
        );
        rewardService.submitMission(mission.missionId(), new SubmitMissionRequest("done"), 2L, "CHILD");

        var rejected = rewardService.rejectMission(mission.missionId(), new RejectMissionRequest("try again"), 1L, "PARENT");

        assertThat(rejected.status()).isEqualTo(RewardTaskStatus.REJECTED);
        assertThat(rejected.rejectReason()).isEqualTo("try again");
    }

    @Test
    void childCannotApproveMission() {
        rewardService.createFamilyLink(new CreateFamilyLinkRequest(2L), 1L, "PARENT");
        var mission = rewardService.createMission(
                new CreateMissionRequest(2L, "Read", "Read for 30 minutes", new BigDecimal("3000")),
                1L,
                "PARENT"
        );

        assertThatThrownBy(() -> rewardService.approveMission(mission.missionId(), 2L, "CHILD"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void cashbookSummaryUsesPaidMissionsAndWalletBalance() {
        rewardService.createFamilyLink(new CreateFamilyLinkRequest(2L), 1L, "PARENT");
        var mission = rewardService.createMission(
                new CreateMissionRequest(2L, "Read", "Read for 30 minutes", new BigDecimal("3000")),
                1L,
                "PARENT"
        );
        rewardService.submitMission(mission.missionId(), new SubmitMissionRequest("done"), 2L, "CHILD");
        rewardService.approveMission(mission.missionId(), 1L, "PARENT");
        rewardService.payMission(mission.missionId(), 1L, "PARENT");

        var summary = rewardService.getCashbookSummary(2L, 1L, "PARENT");
        var entries = rewardService.getCashbookEntries(2L, 2L, "CHILD");

        assertThat(summary.walletBalance()).isEqualByComparingTo("3000");
        assertThat(summary.paidRewardAmount()).isEqualByComparingTo("3000");
        assertThat(summary.paidMissionCount()).isEqualTo(1);
        assertThat(entries).hasSize(1);
    }

    @Test
    void parentCreditSummaryUsesParentWalletPaidMissionsAndPendingApprovals() {
        rewardService.createFamilyLink(new CreateFamilyLinkRequest(2L), 1L, "PARENT");
        var paidMission = rewardService.createMission(
                new CreateMissionRequest(2L, "Read", "Read for 30 minutes", new BigDecimal("3000")),
                1L,
                "PARENT"
        );
        var submittedMission = rewardService.createMission(
                new CreateMissionRequest(2L, "Clean", "Clean the desk", new BigDecimal("1000")),
                1L,
                "PARENT"
        );
        rewardService.submitMission(paidMission.missionId(), new SubmitMissionRequest("done"), 2L, "CHILD");
        rewardService.approveMission(paidMission.missionId(), 1L, "PARENT");
        rewardService.payMission(paidMission.missionId(), 1L, "PARENT");
        rewardService.submitMission(submittedMission.missionId(), new SubmitMissionRequest("done"), 2L, "CHILD");

        var summary = rewardService.getParentCreditSummary(1L, "PARENT");

        assertThat(summary.walletId()).isEqualTo(10L);
        assertThat(summary.creditBalance()).isEqualByComparingTo("50000");
        assertThat(summary.monthlyRewardPaid()).isEqualByComparingTo("3000");
        assertThat(summary.pendingApprovalCount()).isEqualTo(1);
    }
}
