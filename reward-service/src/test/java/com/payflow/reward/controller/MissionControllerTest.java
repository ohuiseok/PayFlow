package com.payflow.reward.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.reward.client.CreateTransferRequest;
import com.payflow.reward.client.TransferClient;
import com.payflow.reward.client.TransferResponse;
import com.payflow.reward.client.WalletClient;
import com.payflow.reward.client.WalletResponse;
import com.payflow.reward.dto.CreateFamilyLinkRequest;
import com.payflow.reward.dto.CreateMissionRequest;
import com.payflow.reward.dto.RejectMissionRequest;
import com.payflow.reward.dto.SubmitMissionRequest;
import com.payflow.reward.repository.ParentChildLinkRepository;
import com.payflow.reward.repository.RewardTaskRepository;
import com.payflow.reward.service.RewardService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MissionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired RewardService rewardService;
    @Autowired RewardTaskRepository rewardTaskRepository;
    @Autowired ParentChildLinkRepository parentChildLinkRepository;

    @MockitoBean TransferClient transferClient;
    @MockitoBean WalletClient walletClient;

    private static final Long PARENT_ID = 1L;
    private static final Long CHILD_ID  = 2L;

    @BeforeEach
    void setUp() {
        rewardTaskRepository.deleteAll();
        parentChildLinkRepository.deleteAll();

        // 부모-자녀 연결을 미리 만들어 둔다.
        rewardService.createFamilyLink(new CreateFamilyLinkRequest(CHILD_ID), PARENT_ID, "ROLE_PARENT");

        when(transferClient.createTransfer(any(CreateTransferRequest.class), anyString(), eq(PARENT_ID), anyString()))
                .thenReturn(new TransferResponse(100L, PARENT_ID, CHILD_ID, new BigDecimal("3000"), "SUCCEEDED", null));
        when(walletClient.getWalletByUserId(eq(PARENT_ID), eq(true), any()))
                .thenReturn(new WalletResponse(10L, PARENT_ID, new BigDecimal("50000"), "ACTIVE"));
        when(walletClient.getWalletByUserId(eq(CHILD_ID), eq(true), any()))
                .thenReturn(new WalletResponse(20L, CHILD_ID, new BigDecimal("3000"), "ACTIVE"));
    }

    // ── 미션 등록 ─────────────────────────────────────────────────────────────

    @Test
    void createMissionReturns201WithMissionId() throws Exception {
        var request = new CreateMissionRequest(CHILD_ID, "방 청소하기", "방 청소 후 사진 찍어 제출", new BigDecimal("3000"));

        mockMvc.perform(post("/missions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-User-Id", PARENT_ID)
                        .header("X-User-Role", "ROLE_PARENT"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.missionId").isNumber())
                .andExpect(jsonPath("$.title").value("방 청소하기"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.parentUserId").value(PARENT_ID))
                .andExpect(jsonPath("$.childUserId").value(CHILD_ID));
    }

    @Test
    void createMissionRejects403WhenCallerIsChild() throws Exception {
        var request = new CreateMissionRequest(CHILD_ID, "방 청소하기", "설명", new BigDecimal("3000"));

        mockMvc.perform(post("/missions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-User-Id", CHILD_ID)
                        .header("X-User-Role", "ROLE_CHILD"))
                .andExpect(status().isForbidden());
    }

    // ── 미션 목록/상세 조회 ──────────────────────────────────────────────────

    @Test
    void getMissionsReturnsParentsMissions() throws Exception {
        rewardService.createMission(
                new CreateMissionRequest(CHILD_ID, "설거지", "설명", new BigDecimal("1000")),
                PARENT_ID, "ROLE_PARENT");

        mockMvc.perform(get("/missions")
                        .header("X-User-Id", PARENT_ID)
                        .header("X-User-Role", "ROLE_PARENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("설거지"));
    }

    @Test
    void getMissionReturnsDetailById() throws Exception {
        var mission = rewardService.createMission(
                new CreateMissionRequest(CHILD_ID, "독서하기", "설명", new BigDecimal("2000")),
                PARENT_ID, "ROLE_PARENT");

        mockMvc.perform(get("/missions/{missionId}", mission.missionId())
                        .header("X-User-Id", PARENT_ID)
                        .header("X-User-Role", "ROLE_PARENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missionId").value(mission.missionId()))
                .andExpect(jsonPath("$.title").value("독서하기"));
    }

    // ── 자녀 제출 ─────────────────────────────────────────────────────────────

    @Test
    void childSubmitsMissionChangesStatusToSubmitted() throws Exception {
        var mission = rewardService.createMission(
                new CreateMissionRequest(CHILD_ID, "영어 공부", "설명", new BigDecimal("3000")),
                PARENT_ID, "ROLE_PARENT");

        mockMvc.perform(patch("/missions/{missionId}/submit", mission.missionId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SubmitMissionRequest("완료했어요")))
                        .header("X-User-Id", CHILD_ID)
                        .header("X-User-Role", "ROLE_CHILD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submissionNote").value("완료했어요"));
    }

    // ── 부모 승인 ─────────────────────────────────────────────────────────────

    @Test
    void parentApprovesMissionChangesStatusToApproved() throws Exception {
        var mission = rewardService.createMission(
                new CreateMissionRequest(CHILD_ID, "운동하기", "설명", new BigDecimal("3000")),
                PARENT_ID, "ROLE_PARENT");
        rewardService.submitMission(mission.missionId(), new SubmitMissionRequest("완료"), CHILD_ID, "ROLE_CHILD");

        mockMvc.perform(patch("/missions/{missionId}/approve", mission.missionId())
                        .header("X-User-Id", PARENT_ID)
                        .header("X-User-Role", "ROLE_PARENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    // ── 부모 반려 ─────────────────────────────────────────────────────────────

    @Test
    void parentRejectsMissionChangesStatusToRejected() throws Exception {
        var mission = rewardService.createMission(
                new CreateMissionRequest(CHILD_ID, "수학 풀기", "설명", new BigDecimal("3000")),
                PARENT_ID, "ROLE_PARENT");
        rewardService.submitMission(mission.missionId(), new SubmitMissionRequest("완료"), CHILD_ID, "ROLE_CHILD");

        mockMvc.perform(patch("/missions/{missionId}/reject", mission.missionId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RejectMissionRequest("사진이 없어요")))
                        .header("X-User-Id", PARENT_ID)
                        .header("X-User-Role", "ROLE_PARENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value("사진이 없어요"));
    }

    // ── 보상 지급 ─────────────────────────────────────────────────────────────

    @Test
    void payMissionChangesStatusToPaid() throws Exception {
        var mission = rewardService.createMission(
                new CreateMissionRequest(CHILD_ID, "피아노 연습", "설명", new BigDecimal("3000")),
                PARENT_ID, "ROLE_PARENT");
        rewardService.submitMission(mission.missionId(), new SubmitMissionRequest("완료"), CHILD_ID, "ROLE_CHILD");
        rewardService.approveMission(mission.missionId(), PARENT_ID, "ROLE_PARENT");

        mockMvc.perform(post("/missions/{missionId}/pay", mission.missionId())
                        .header("X-User-Id", PARENT_ID)
                        .header("X-User-Role", "ROLE_PARENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.transferId").value(100L));
    }

    @Test
    void payMissionIsIdempotentWhenAlreadyPaid() throws Exception {
        var mission = rewardService.createMission(
                new CreateMissionRequest(CHILD_ID, "줄넘기", "설명", new BigDecimal("3000")),
                PARENT_ID, "ROLE_PARENT");
        rewardService.submitMission(mission.missionId(), new SubmitMissionRequest("완료"), CHILD_ID, "ROLE_CHILD");
        rewardService.approveMission(mission.missionId(), PARENT_ID, "ROLE_PARENT");
        rewardService.payMission(mission.missionId(), PARENT_ID, "ROLE_PARENT");

        // 이미 PAID인 미션에 재지급 요청 → 200 반환 (중복 지급 없음)
        mockMvc.perform(post("/missions/{missionId}/pay", mission.missionId())
                        .header("X-User-Id", PARENT_ID)
                        .header("X-User-Role", "ROLE_PARENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void payMissionRejects403WhenCallerIsChild() throws Exception {
        var mission = rewardService.createMission(
                new CreateMissionRequest(CHILD_ID, "청소", "설명", new BigDecimal("1000")),
                PARENT_ID, "ROLE_PARENT");
        rewardService.submitMission(mission.missionId(), new SubmitMissionRequest("완료"), CHILD_ID, "ROLE_CHILD");
        rewardService.approveMission(mission.missionId(), PARENT_ID, "ROLE_PARENT");

        mockMvc.perform(post("/missions/{missionId}/pay", mission.missionId())
                        .header("X-User-Id", CHILD_ID)
                        .header("X-User-Role", "ROLE_CHILD"))
                .andExpect(status().isForbidden());
    }
}
