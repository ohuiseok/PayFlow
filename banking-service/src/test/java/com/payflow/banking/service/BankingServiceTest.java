package com.payflow.banking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.payflow.banking.client.WalletBalanceChangeRequest;
import com.payflow.banking.client.WalletClient;
import com.payflow.banking.client.WalletResponse;
import com.payflow.banking.dto.CreateBankAccountRequest;
import com.payflow.banking.dto.CreateDepositRequest;
import com.payflow.banking.entity.BankingTransferStatus;
import com.payflow.banking.repository.BankAccountRepository;
import com.payflow.banking.repository.BankingTransferRepository;
import com.payflow.banking.support.error.BusinessException;
import com.payflow.banking.support.error.ErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class BankingServiceTest {

    @Autowired
    BankingService bankingService;

    @Autowired
    BankAccountRepository bankAccountRepository;

    @Autowired
    BankingTransferRepository bankingTransferRepository;

    @MockitoBean
    WalletClient walletClient;

    @BeforeEach
    void setUp() {
        bankingTransferRepository.deleteAll();
        bankAccountRepository.deleteAll();
        when(walletClient.getWalletByUserId(eq(1L), eq(true), any()))
                .thenReturn(new WalletResponse(10L, 1L, BigDecimal.ZERO, "ACTIVE"));
        when(walletClient.deposit(eq(10L), any(WalletBalanceChangeRequest.class), eq(true), any()))
                .thenReturn(new WalletResponse(10L, 1L, new BigDecimal("50000"), "ACTIVE"));
    }

    @Test
    void createBankAccountMasksAccountNumber() {
        var response = bankingService.createBankAccount(
                new CreateBankAccountRequest("004", "123-456-7890", "Parent"),
                1L
        );

        assertThat(response.bankCode()).isEqualTo("004");
        assertThat(response.accountNumberMasked()).isEqualTo("123-****-7890");
        assertThat(response.accountHolderName()).isEqualTo("Parent");
    }

    @Test
    void createBankAccountRejectsDuplicateForSameUser() {
        var request = new CreateBankAccountRequest("004", "1234567890", "Parent");
        bankingService.createBankAccount(request, 1L);

        assertThatThrownBy(() -> bankingService.createBankAccount(request, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_BANK_ACCOUNT);
    }

    @Test
    void getBankAccountsReturnsOnlyRequestUsersAccounts() {
        bankingService.createBankAccount(new CreateBankAccountRequest("004", "1234567890", "Parent"), 1L);
        bankingService.createBankAccount(new CreateBankAccountRequest("088", "9876543210", "Other"), 2L);

        var responses = bankingService.getBankAccounts(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().bankCode()).isEqualTo("004");
    }

    @Test
    void createDepositDepositsToWalletAndRecordsSucceededStatus() {
        var account = bankingService.createBankAccount(new CreateBankAccountRequest("004", "1234567890", "Parent"), 1L);

        var response = bankingService.createDeposit(
                new CreateDepositRequest(account.bankAccountId(), new BigDecimal("50000")),
                "deposit-key-1",
                1L
        );

        assertThat(response.status()).isEqualTo(BankingTransferStatus.SUCCEEDED);
        assertThat(response.walletId()).isEqualTo(10L);
        assertThat(response.amount()).isEqualByComparingTo("50000");
        verify(walletClient).deposit(eq(10L), any(WalletBalanceChangeRequest.class), eq(true), any());
    }

    @Test
    void duplicateIdempotencyKeyWithSameRequestReturnsExistingResult() {
        var account = bankingService.createBankAccount(new CreateBankAccountRequest("004", "1234567890", "Parent"), 1L);
        var request = new CreateDepositRequest(account.bankAccountId(), new BigDecimal("50000"));

        bankingService.createDeposit(request, "deposit-key-1", 1L);
        var response = bankingService.createDeposit(request, "deposit-key-1", 1L);

        assertThat(response.status()).isEqualTo(BankingTransferStatus.SUCCEEDED);
        assertThat(bankingTransferRepository.count()).isEqualTo(1);
        verify(walletClient, times(1)).deposit(eq(10L), any(WalletBalanceChangeRequest.class), eq(true), any());
    }

    @Test
    void duplicateIdempotencyKeyWithDifferentRequestFails() {
        var account = bankingService.createBankAccount(new CreateBankAccountRequest("004", "1234567890", "Parent"), 1L);
        bankingService.createDeposit(new CreateDepositRequest(account.bankAccountId(), new BigDecimal("50000")), "deposit-key-1", 1L);

        assertThatThrownBy(() -> bankingService.createDeposit(
                new CreateDepositRequest(account.bankAccountId(), new BigDecimal("10000")),
                "deposit-key-1",
                1L
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IDEMPOTENCY_REQUEST_MISMATCH);
    }

    @Test
    void createDepositRejectsOtherUsersBankAccount() {
        var account = bankingService.createBankAccount(new CreateBankAccountRequest("004", "1234567890", "Parent"), 1L);

        assertThatThrownBy(() -> bankingService.createDeposit(
                new CreateDepositRequest(account.bankAccountId(), new BigDecimal("50000")),
                "deposit-key-1",
                2L
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BANK_ACCOUNT_NOT_FOUND);
    }

    @Test
    void walletFailureRecordsFailedStatus() {
        var account = bankingService.createBankAccount(new CreateBankAccountRequest("004", "1234567890", "Parent"), 1L);
        when(walletClient.deposit(eq(10L), any(WalletBalanceChangeRequest.class), eq(true), any()))
                .thenThrow(new RuntimeException("wallet unavailable"));

        var response = bankingService.createDeposit(
                new CreateDepositRequest(account.bankAccountId(), new BigDecimal("50000")),
                "deposit-key-1",
                1L
        );

        assertThat(response.status()).isEqualTo(BankingTransferStatus.FAILED);
        assertThat(response.failureReason()).contains("wallet unavailable");
    }
}
