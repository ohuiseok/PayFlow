package com.payflow.reward.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.payflow.reward.repository.ParentChildLinkRepository;
import com.payflow.reward.repository.RewardTaskRepository;
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
class FamilyControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ParentChildLinkRepository parentChildLinkRepository;
    @Autowired RewardTaskRepository rewardTaskRepository;

    @MockitoBean TransferClient transferClient;
    @MockitoBean WalletClient walletClient;

    private static final Long PARENT_ID  = 1L;
    private static final Long CHILD_ID   = 2L;
    private static final Long CHILD_ID_2 = 3L;

    @BeforeEach
    void setUp() {
        rewardTaskRepository.deleteAll();
        parentChildLinkRepository.deleteAll();

        when(transferClient.createTransfer(any(CreateTransferRequest.class), anyString(), eq(PARENT_ID), anyString()))
                .thenReturn(new TransferResponse(100L, PARENT_ID, CHILD_ID, BigDecimal.ZERO, "SUCCEEDED", null));
        when(walletClient.getWalletByUserId(any(), eq(true), any()))
                .thenReturn(new WalletResponse(10L, PARENT_ID, new BigDecimal("50000"), "ACTIVE"));
    }

    // ── 가족 연결 생성 ────────────────────────────────────────────────────────

    @Test
    void createFamilyLinkReturns201() throws Exception {
        mockMvc.perform(post("/families/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateFamilyLinkRequest(CHILD_ID)))
                        .header("X-User-Id", PARENT_ID)
                        .header("X-User-Role", "ROLE_PARENT"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.familyLinkId").isNumber())
                .andExpect(jsonPath("$.parentUserId").value(PARENT_ID))
                .andExpect(jsonPath("$.childUserId").value(CHILD_ID))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createFamilyLinkRejects403WhenCallerIsChild() throws Exception {
        mockMvc.perform(post("/families/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateFamilyLinkRequest(CHILD_ID)))
                        .header("X-User-Id", CHILD_ID)
                        .header("X-User-Role", "ROLE_CHILD"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createDuplicateFamilyLinkReturns409() throws Exception {
        mockMvc.perform(post("/families/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateFamilyLinkRequest(CHILD_ID)))
                        .header("X-User-Id", PARENT_ID)
                        .header("X-User-Role", "ROLE_PARENT"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/families/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateFamilyLinkRequest(CHILD_ID)))
                        .header("X-User-Id", PARENT_ID)
                        .header("X-User-Role", "ROLE_PARENT"))
                .andExpect(status().isConflict());
    }

    // ── 자녀 목록 조회 ────────────────────────────────────────────────────────

    @Test
    void getChildrenReturnsLinkedChildren() throws Exception {
        parentChildLinkRepository.deleteAll();

        mockMvc.perform(post("/families/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateFamilyLinkRequest(CHILD_ID)))
                        .header("X-User-Id", PARENT_ID)
                        .header("X-User-Role", "ROLE_PARENT"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/families/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateFamilyLinkRequest(CHILD_ID_2)))
                        .header("X-User-Id", PARENT_ID)
                        .header("X-User-Role", "ROLE_PARENT"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/families/children")
                        .header("X-User-Id", PARENT_ID)
                        .header("X-User-Role", "ROLE_PARENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].parentUserId").value(PARENT_ID));
    }

    @Test
    void getChildrenReturnsEmptyListWhenNoLinks() throws Exception {
        mockMvc.perform(get("/families/children")
                        .header("X-User-Id", PARENT_ID)
                        .header("X-User-Role", "ROLE_PARENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── 부모 목록 조회 ────────────────────────────────────────────────────────

    @Test
    void getParentsReturnsLinkedParents() throws Exception {
        mockMvc.perform(post("/families/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateFamilyLinkRequest(CHILD_ID)))
                        .header("X-User-Id", PARENT_ID)
                        .header("X-User-Role", "ROLE_PARENT"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/families/parents")
                        .header("X-User-Id", CHILD_ID)
                        .header("X-User-Role", "ROLE_CHILD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].childUserId").value(CHILD_ID))
                .andExpect(jsonPath("$[0].parentUserId").value(PARENT_ID));
    }
}
