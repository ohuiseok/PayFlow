package com.payflow.wallet.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.payflow.wallet.dto.CreateWalletRequest;
import com.payflow.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WalletControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    WalletService walletService;

    @Test
    void createWalletReturnsCreated() throws Exception {
        mockMvc.perform(post("/wallets")
                        .header("X-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void getWalletRejectsOwnerMismatch() throws Exception {
        var wallet = walletService.createWallet(new CreateWalletRequest(1L), 1L);

        mockMvc.perform(get("/wallets/{walletId}", wallet.walletId())
                        .header("X-User-Id", 2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESOURCE_OWNER_MISMATCH"));
    }

    @Test
    void withdrawRequiresInternalHeader() throws Exception {
        var wallet = walletService.createWallet(new CreateWalletRequest(1L), 1L);

        mockMvc.perform(post("/wallets/{walletId}/withdraw", wallet.walletId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": 1000,
                                  "referenceType": "TRANSFER",
                                  "referenceId": "1001"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
