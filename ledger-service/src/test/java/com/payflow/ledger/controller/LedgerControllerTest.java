package com.payflow.ledger.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.ledger.dto.PaymentLedgerRequest;
import com.payflow.ledger.entity.LedgerEntryType;
import com.payflow.ledger.entity.LedgerSourceType;
import com.payflow.ledger.event.TransferFailedEvent;
import com.payflow.ledger.repository.LedgerEntryRepository;
import com.payflow.ledger.repository.TransferFailureEventRepository;
import com.payflow.ledger.service.LedgerService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LedgerControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LedgerService ledgerService;

    @Autowired
    LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    TransferFailureEventRepository transferFailureEventRepository;

    @BeforeEach
    void setUp() {
        transferFailureEventRepository.deleteAll();
        ledgerEntryRepository.deleteAll();
    }

    @Test
    void recordPaymentChargeReturnsLedgerEntry() throws Exception {
        var request = new PaymentLedgerRequest(
                LedgerSourceType.TOSS_CHARGE,
                300L,
                LedgerEntryType.USER_WALLET_TOPUP,
                1L,
                new BigDecimal("10000")
        );

        mockMvc.perform(post("/ledgers/internal/payment-charge")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceType").value("TOSS_CHARGE"))
                .andExpect(jsonPath("$.sourceId").value(300))
                .andExpect(jsonPath("$.entryType").value("USER_WALLET_TOPUP"))
                .andExpect(jsonPath("$.lines.length()").value(2));
    }

    @Test
    void getLedgerEntriesReturnsRecentEntries() throws Exception {
        ledgerService.recordPaymentCharge(new PaymentLedgerRequest(
                LedgerSourceType.TOSS_CHARGE,
                300L,
                LedgerEntryType.USER_WALLET_TOPUP,
                1L,
                new BigDecimal("10000")
        ));

        mockMvc.perform(get("/ledgers/entries")
                        .header("X-User-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceType").value("TOSS_CHARGE"))
                .andExpect(jsonPath("$[0].lines.length()").value(2));
    }

    @Test
    void getTransferFailuresReturnsFailureEvents() throws Exception {
        ledgerService.recordTransferFailure(new TransferFailedEvent(200L, 1L, 2L, new BigDecimal("3000"), "FAILED", "wallet timeout"));

        mockMvc.perform(get("/ledgers/transfer-failures")
                        .header("X-User-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transferId").value(200))
                .andExpect(jsonPath("$[0].senderUserId").value(1))
                .andExpect(jsonPath("$[0].receiverUserId").value(2))
                .andExpect(jsonPath("$[0].amount").value(3000))
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].failureReason").value("wallet timeout"))
                .andExpect(jsonPath("$[0].createdAt").exists());
    }

    @Test
    void getTransferFailureReturnsFailureEvent() throws Exception {
        ledgerService.recordTransferFailure(new TransferFailedEvent(200L, 1L, 2L, new BigDecimal("3000"), "FAILED", "wallet timeout"));

        mockMvc.perform(get("/ledgers/transfer-failures/{transferId}", 200L)
                        .header("X-User-Id", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferId").value(200))
                .andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void getTransferFailureReturnsNotFoundWhenMissing() throws Exception {
        mockMvc.perform(get("/ledgers/transfer-failures/{transferId}", 999L)
                        .header("X-User-Id", 1L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }
}
