package com.payflow.transfer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.payflow.transfer.client.WalletBalanceChangeRequest;
import com.payflow.transfer.client.WalletClient;
import com.payflow.transfer.client.WalletResponse;
import com.payflow.transfer.dto.CreateTransferRequest;
import com.payflow.transfer.event.TransferEventPublisher;
import com.payflow.transfer.lock.DistributedLock;
import com.payflow.transfer.repository.TransferRepository;
import com.payflow.transfer.service.TransferService;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransferCompensationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TransferService transferService;

    @Autowired
    TransferRepository transferRepository;

    @MockitoBean
    WalletClient walletClient;

    @MockitoBean
    TransferEventPublisher transferEventPublisher;

    @MockitoBean
    DistributedLock distributedLock;

    @BeforeEach
    void setUp() {
        transferRepository.deleteAll();
        when(distributedLock.tryLock(any(), any(), any(Duration.class))).thenReturn(true);
        when(walletClient.getWalletByUserId(eq(1L), eq(true), any())).thenReturn(new WalletResponse(10L, 1L, new BigDecimal("10000"), "ACTIVE"));
        when(walletClient.getWalletByUserId(eq(2L), eq(true), any())).thenReturn(new WalletResponse(20L, 2L, BigDecimal.ZERO, "ACTIVE"));
        when(walletClient.withdraw(eq(10L), any(WalletBalanceChangeRequest.class), eq(true), any()))
                .thenReturn(new WalletResponse(10L, 1L, new BigDecimal("7000"), "ACTIVE"));
        when(walletClient.deposit(eq(20L), any(WalletBalanceChangeRequest.class), eq(true), any()))
                .thenThrow(new RuntimeException("receiver deposit failed"));
    }

    @Test
    void getCompensationsReturnsCompensationRequiredTransfers() throws Exception {
        var compensation = transferService.createTransfer(new CreateTransferRequest(2L, new BigDecimal("3000")), "key-comp", 1L);

        mockMvc.perform(get("/transfers/compensations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transferId").value(compensation.transferId()))
                .andExpect(jsonPath("$[0].status").value("COMPENSATION_REQUIRED"));
    }

    @Test
    void getCompensationReturnsTransfer() throws Exception {
        var compensation = transferService.createTransfer(new CreateTransferRequest(2L, new BigDecimal("3000")), "key-comp", 1L);

        mockMvc.perform(get("/transfers/compensations/{transferId}", compensation.transferId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferId").value(compensation.transferId()))
                .andExpect(jsonPath("$.status").value("COMPENSATION_REQUIRED"));
    }

    @Test
    void refundCompensationMarksTransferCompensated() throws Exception {
        var compensation = transferService.createTransfer(new CreateTransferRequest(2L, new BigDecimal("3000")), "key-comp", 1L);
        when(walletClient.deposit(eq(10L), any(WalletBalanceChangeRequest.class), eq(true), any()))
                .thenReturn(new WalletResponse(10L, 1L, new BigDecimal("10000"), "ACTIVE"));

        mockMvc.perform(post("/transfers/compensations/{transferId}/refund", compensation.transferId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferId").value(compensation.transferId()))
                .andExpect(jsonPath("$.status").value("COMPENSATED"));
    }
}
