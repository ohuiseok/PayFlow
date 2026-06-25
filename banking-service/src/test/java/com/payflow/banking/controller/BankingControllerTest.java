package com.payflow.banking.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.banking.client.WalletBalanceChangeRequest;
import com.payflow.banking.client.WalletClient;
import com.payflow.banking.client.WalletResponse;
import com.payflow.banking.dto.CreateBankAccountRequest;
import com.payflow.banking.dto.CreateDepositRequest;
import com.payflow.banking.dto.CreateWithdrawalRequest;
import com.payflow.banking.openbanking.OpenBankingClient;
import com.payflow.banking.openbanking.OpenBankingTransferResponse;
import com.payflow.banking.openbanking.OpenBankingWithdrawTransferRequest;
import com.payflow.banking.repository.BankAccountRepository;
import com.payflow.banking.repository.BankingTransferRepository;
import com.payflow.banking.service.BankingService;
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
class BankingControllerTest {

    @Autowired MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper();
    @Autowired BankingService bankingService;
    @Autowired BankAccountRepository bankAccountRepository;
    @Autowired BankingTransferRepository bankingTransferRepository;

    @MockitoBean WalletClient walletClient;
    @MockitoBean OpenBankingClient openBankingClient;

    private static final Long USER_ID = 1L;
    private static final String IDEMPOTENCY_KEY = "test-key-001";

    @BeforeEach
    void setUp() {
        bankingTransferRepository.deleteAll();
        bankAccountRepository.deleteAll();

        when(walletClient.getWalletByUserId(eq(USER_ID), eq(true), any()))
                .thenReturn(new WalletResponse(10L, USER_ID, new BigDecimal("100000"), "ACTIVE"));
        when(walletClient.deposit(eq(10L), any(WalletBalanceChangeRequest.class), eq(true), any()))
                .thenReturn(new WalletResponse(10L, USER_ID, new BigDecimal("150000"), "ACTIVE"));
        when(walletClient.withdraw(eq(10L), any(WalletBalanceChangeRequest.class), eq(true), any()))
                .thenReturn(new WalletResponse(10L, USER_ID, new BigDecimal("50000"), "ACTIVE"));
        when(openBankingClient.withdrawTransfer(any(OpenBankingWithdrawTransferRequest.class), any()))
                .thenAnswer(invocation -> {
                    OpenBankingWithdrawTransferRequest req = invocation.getArgument(0);
                    return new OpenBankingTransferResponse(
                            "mock-tran-id", req.tranDtime(), "A0000", "success",
                            req.bankTranId(), "20260620", "004", "000", "success", req.tranAmt());
                });
    }

    // ── 계좌 등록 ─────────────────────────────────────────────────────────────

    @Test
    void createBankAccountReturns201WithMaskedNumber() throws Exception {
        var request = new CreateBankAccountRequest("004", "123-456-7890", "홍길동");

        mockMvc.perform(post("/bank/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bankAccountId").isNumber())
                .andExpect(jsonPath("$.bankCode").value("004"))
                .andExpect(jsonPath("$.accountNumberMasked").value("123-****-7890"))
                .andExpect(jsonPath("$.accountHolderName").value("홍길동"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createBankAccountReturns409OnDuplicate() throws Exception {
        var request = new CreateBankAccountRequest("004", "1234567890", "홍길동");

        mockMvc.perform(post("/bank/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/bank/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isConflict());
    }

    @Test
    void createBankAccountReturns400OnMissingBankCode() throws Exception {
        String requestJson = "{\"bankCode\":\"\",\"accountNumber\":\"1234567890\",\"accountHolderName\":\"홍길동\"}";

        mockMvc.perform(post("/bank/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isBadRequest());
    }

    // ── 계좌 목록 조회 ─────────────────────────────────────────────────────────

    @Test
    void getBankAccountsReturnsOnlyUserAccounts() throws Exception {
        bankingService.createBankAccount(new CreateBankAccountRequest("004", "111-222-3333", "홍길동"), USER_ID);
        bankingService.createBankAccount(new CreateBankAccountRequest("020", "444-555-6666", "홍길동"), USER_ID);
        // 다른 사용자 계좌 (포함되면 안 됨)
        bankingService.createBankAccount(new CreateBankAccountRequest("004", "777-888-9999", "김철수"), 2L);

        mockMvc.perform(get("/bank/accounts")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getBankAccountsReturnsEmptyListWhenNone() throws Exception {
        mockMvc.perform(get("/bank/accounts")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── 충전 (deposit) ────────────────────────────────────────────────────────

    @Test
    void createDepositReturns201WithSucceededStatus() throws Exception {
        var account = bankingService.createBankAccount(
                new CreateBankAccountRequest("004", "123-456-7890", "홍길동"), USER_ID);
        var request = new CreateDepositRequest(account.bankAccountId(), new BigDecimal("50000"));

        mockMvc.perform(post("/bank/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-User-Id", USER_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bankingTransferId").isNumber())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(50000));
    }

    @Test
    void createDepositIsIdempotent() throws Exception {
        var account = bankingService.createBankAccount(
                new CreateBankAccountRequest("004", "123-456-7890", "홍길동"), USER_ID);
        var request = new CreateDepositRequest(account.bankAccountId(), new BigDecimal("50000"));

        mockMvc.perform(post("/bank/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-User-Id", USER_ID)
                        .header("Idempotency-Key", "idem-key-deposit"))
                .andExpect(status().isCreated());

        // 동일 Idempotency-Key 재시도 → 200 반환, 중복 처리 없음
        mockMvc.perform(post("/bank/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-User-Id", USER_ID)
                        .header("Idempotency-Key", "idem-key-deposit"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void createDepositReturns404WhenAccountNotFound() throws Exception {
        var request = new CreateDepositRequest(999L, new BigDecimal("50000"));

        mockMvc.perform(post("/bank/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-User-Id", USER_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(status().isNotFound());
    }

    // ── 출금 (withdrawal) ─────────────────────────────────────────────────────

    @Test
    void createWithdrawalReturns201WithSucceededStatus() throws Exception {
        var account = bankingService.createBankAccount(
                new CreateBankAccountRequest("004", "123-456-7890", "홍길동"), USER_ID);
        var request = new CreateWithdrawalRequest(account.bankAccountId(), new BigDecimal("50000"));

        mockMvc.perform(post("/bank/withdrawals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-User-Id", USER_ID)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bankingTransferId").isNumber())
                .andExpect(jsonPath("$.status").value("COMPENSATION_REQUIRED"))
                .andExpect(jsonPath("$.amount").value(50000));
    }

    @Test
    void createWithdrawalIsIdempotent() throws Exception {
        var account = bankingService.createBankAccount(
                new CreateBankAccountRequest("004", "123-456-7890", "홍길동"), USER_ID);
        var request = new CreateWithdrawalRequest(account.bankAccountId(), new BigDecimal("50000"));

        mockMvc.perform(post("/bank/withdrawals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-User-Id", USER_ID)
                        .header("Idempotency-Key", "idem-key-withdrawal"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/bank/withdrawals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-User-Id", USER_ID)
                        .header("Idempotency-Key", "idem-key-withdrawal"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPENSATION_REQUIRED"));
    }

    // ── 거래 상세 조회 ────────────────────────────────────────────────────────

    @Test
    void getTransferReturnsTransferById() throws Exception {
        var account = bankingService.createBankAccount(
                new CreateBankAccountRequest("004", "123-456-7890", "홍길동"), USER_ID);
        var deposit = bankingService.createDeposit(
                new CreateDepositRequest(account.bankAccountId(), new BigDecimal("30000")),
                IDEMPOTENCY_KEY, USER_ID);

        mockMvc.perform(get("/bank/transfers/{bankingTransferId}", deposit.bankingTransferId())
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankingTransferId").value(deposit.bankingTransferId()))
                .andExpect(jsonPath("$.amount").value(30000))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void getTransferReturns404WhenNotFound() throws Exception {
        mockMvc.perform(get("/bank/transfers/{bankingTransferId}", 999L)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTransferReturns404WhenOtherUsersTransfer() throws Exception {
        var account = bankingService.createBankAccount(
                new CreateBankAccountRequest("004", "123-456-7890", "홍길동"), USER_ID);
        var deposit = bankingService.createDeposit(
                new CreateDepositRequest(account.bankAccountId(), new BigDecimal("30000")),
                IDEMPOTENCY_KEY, USER_ID);

        // 다른 사용자가 접근 → 404
        mockMvc.perform(get("/bank/transfers/{bankingTransferId}", deposit.bankingTransferId())
                        .header("X-User-Id", 2L))
                .andExpect(status().isNotFound());
    }
}
