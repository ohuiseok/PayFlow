package com.payflow.banking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.payflow.banking.client.WalletBalanceChangeRequest;
import com.payflow.banking.client.WalletClient;
import com.payflow.banking.client.WalletResponse;
import com.payflow.banking.dto.CreateBankAccountRequest;
import com.payflow.banking.dto.CreateDepositRequest;
import com.payflow.banking.dto.CreateWithdrawalRequest;
import com.payflow.banking.entity.BankingTransferType;
import com.payflow.banking.dto.OpenBankingCallbackRequest;
import com.payflow.banking.entity.BankingTransferStatus;
import com.payflow.banking.openbanking.OpenBankingClient;
import com.payflow.banking.openbanking.OpenBankingDepositTransferRequest;
import com.payflow.banking.openbanking.OpenBankingTokenResponse;
import com.payflow.banking.openbanking.OpenBankingTransferResultRequest;
import com.payflow.banking.openbanking.OpenBankingTransferResultResponse;
import com.payflow.banking.openbanking.OpenBankingTransferResponse;
import com.payflow.banking.openbanking.OpenBankingUserMeResponse;
import com.payflow.banking.openbanking.OpenBankingWithdrawTransferRequest;
import com.payflow.banking.repository.BankAccountRepository;
import com.payflow.banking.repository.BankingTransferRepository;
import com.payflow.banking.support.error.BusinessException;
import com.payflow.banking.support.error.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
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

    @MockitoBean
    OpenBankingClient openBankingClient;

    @BeforeEach
    void setUp() {
        bankingTransferRepository.deleteAll();
        bankAccountRepository.deleteAll();
        when(openBankingClient.exchangeAuthorizationCode(eq("auth-code-1")))
                .thenReturn(new OpenBankingTokenResponse(
                        "user-token-1",
                        "refresh-token-1",
                        "Bearer",
                        3600L,
                        "login inquiry transfer",
                        null,
                        "user-seq-1"
                ));
        when(openBankingClient.getUserMe(eq("user-seq-1"), eq("user-token-1")))
                .thenReturn(new OpenBankingUserMeResponse(
                        "api-tran-id",
                        "20260619120000000",
                        "A0000",
                        "success",
                        "user-seq-1",
                        "Parent",
                        "1",
                        List.of(new OpenBankingUserMeResponse.Account(
                                "fintech-use-num-1",
                                "main",
                                "004",
                                "KB",
                                "123-****-7890",
                                "Parent",
                                "Y",
                                "Y"
                        ))
                ));
        when(openBankingClient.withdrawTransfer(any(OpenBankingWithdrawTransferRequest.class), any()))
                .thenAnswer(invocation -> {
                    OpenBankingWithdrawTransferRequest request = invocation.getArgument(0);
                    return new OpenBankingTransferResponse(
                            "mock-api-tran-id",
                            request.tranDtime(),
                            "A0000",
                            "mock success",
                            request.bankTranId(),
                            "20260619",
                            "004",
                            "000",
                            "mock success",
                            request.tranAmt()
                    );
                });
        when(walletClient.getWalletByUserId(eq(1L), eq(true), any()))
                .thenReturn(new WalletResponse(10L, 1L, BigDecimal.ZERO, "ACTIVE"));
        when(walletClient.deposit(eq(10L), any(WalletBalanceChangeRequest.class), eq(true), any()))
                .thenReturn(new WalletResponse(10L, 1L, new BigDecimal("50000"), "ACTIVE"));
        when(walletClient.withdraw(eq(10L), any(WalletBalanceChangeRequest.class), eq(true), any()))
                .thenReturn(new WalletResponse(10L, 1L, BigDecimal.ZERO, "ACTIVE"));
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
    void createAuthorizeUrlUsesOpenBankingSafeQueryParameters() {
        var response = bankingService.createAuthorizeUrl(1L, "parent");

        assertThat(response.authorizeUrl())
                .contains("response_type=code")
                .contains("client_id=test-client-id")
                .contains("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Fbank%2Fopenbanking%2Fcallback")
                .contains("scope=login%20inquiry%20transfer")
                .contains("auth_type=0");
        assertThat(response.state()).matches("[0-9a-f]{32}");
        assertThat(response.authorizeUrl()).contains("state=" + response.state());
    }

    @Test
    void createDepositDepositsToWalletAndRecordsSucceededStatus() {
        linkOpenBankingToken();
        var account = bankingService.createBankAccount(new CreateBankAccountRequest("004", "1234567890", "Parent"), 1L);

        var response = bankingService.createDeposit(
                new CreateDepositRequest(account.bankAccountId(), new BigDecimal("50000")),
                "deposit-key-1",
                1L
        );

        assertThat(response.status()).isEqualTo(BankingTransferStatus.COMPLETED);
        assertThat(response.walletId()).isEqualTo(10L);
        assertThat(response.amount()).isEqualByComparingTo("50000");
        verify(walletClient).deposit(eq(10L), any(WalletBalanceChangeRequest.class), eq(true), any());
    }

    @Test
    void createDepositDoesNotDepositToWalletWhenOpenBankingNeedsResultCheck() {
        linkOpenBankingToken();
        var account = bankingService.createBankAccount(new CreateBankAccountRequest("004", "1234567890", "Parent"), 1L);
        when(openBankingClient.withdrawTransfer(any(OpenBankingWithdrawTransferRequest.class), any()))
                .thenReturn(new OpenBankingTransferResponse(
                        "mock-api-tran-id",
                        "20260619120000",
                        "A0007",
                        "processing",
                        "bank-tran-id",
                        null,
                        null,
                        null,
                        null,
                        "50000"
                ));

        var response = bankingService.createDeposit(
                new CreateDepositRequest(account.bankAccountId(), new BigDecimal("50000")),
                "deposit-key-processing",
                1L
        );

        assertThat(response.status()).isEqualTo(BankingTransferStatus.BANK_PROCESSING);
        verify(walletClient, never()).deposit(eq(10L), any(WalletBalanceChangeRequest.class), eq(true), any());
    }

    @Test
    void createWithdrawalAttemptsDepositTransferAndRequiresCompensation() {
        linkOpenBankingToken();
        var account = bankingService.createBankAccount(new CreateBankAccountRequest("004", "1234567890", "Parent"), 1L);

        var response = bankingService.createWithdrawal(
                new CreateWithdrawalRequest(account.bankAccountId(), new BigDecimal("10000")),
                "withdrawal-key-1",
                1L
        );

        assertThat(response.transferType()).isEqualTo(BankingTransferType.WITHDRAWAL);
        assertThat(response.status()).isEqualTo(BankingTransferStatus.COMPENSATION_REQUIRED);
        assertThat(response.amount()).isEqualByComparingTo("10000");
        verify(walletClient).withdraw(eq(10L), any(WalletBalanceChangeRequest.class), eq(true), any());
        verify(openBankingClient).attemptDepositTransfer(any(OpenBankingDepositTransferRequest.class));
    }

    @Test
    void createWithdrawalFailsWhenWalletWithdrawFailsBeforeBankAttempt() {
        linkOpenBankingToken();
        var account = bankingService.createBankAccount(new CreateBankAccountRequest("004", "1234567890", "Parent"), 1L);
        when(walletClient.withdraw(eq(10L), any(WalletBalanceChangeRequest.class), eq(true), any()))
                .thenThrow(new RuntimeException("insufficient balance"));

        var response = bankingService.createWithdrawal(
                new CreateWithdrawalRequest(account.bankAccountId(), new BigDecimal("10000")),
                "withdrawal-key-wallet-fail",
                1L
        );

        assertThat(response.status()).isEqualTo(BankingTransferStatus.FAILED);
        assertThat(response.failureReason()).contains("insufficient balance");
        verify(openBankingClient, never()).attemptDepositTransfer(any(OpenBankingDepositTransferRequest.class));
    }

    @Test
    void compensateWithdrawalDepositsRefundAndMarksCompensated() {
        linkOpenBankingToken();
        var account = bankingService.createBankAccount(new CreateBankAccountRequest("004", "1234567890", "Parent"), 1L);
        var withdrawal = bankingService.createWithdrawal(
                new CreateWithdrawalRequest(account.bankAccountId(), new BigDecimal("10000")),
                "withdrawal-key-compensate",
                1L
        );

        var compensated = bankingService.compensateWithdrawal(withdrawal.bankingTransferId(), 1L);

        assertThat(compensated.status()).isEqualTo(BankingTransferStatus.COMPENSATED);
        assertThat(compensated.compensatedAt()).isNotNull();
        verify(walletClient).deposit(eq(10L), any(WalletBalanceChangeRequest.class), eq(true), any());
    }

    @Test
    void syncOpenBankingAccountsStoresAccountMetadataWithoutRawAccountNumber() {
        linkOpenBankingToken();

        var responses = bankingService.syncOpenBankingAccounts(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().accountNumberMasked()).isEqualTo("123-****-7890");
        assertThat(bankAccountRepository.findAll().getFirst().getAccountNumber()).isNotEqualTo("1234567890");
    }

    @Test
    void checkTransferResultReflectsWalletWhenBankResultSucceeds() {
        linkOpenBankingToken();
        var account = bankingService.createBankAccount(new CreateBankAccountRequest("004", "1234567890", "Parent"), 1L);
        when(openBankingClient.withdrawTransfer(any(OpenBankingWithdrawTransferRequest.class), any()))
                .thenReturn(new OpenBankingTransferResponse(
                        "api-tran-id",
                        "20260619120000",
                        "A0007",
                        "processing",
                        "bank-tran-id",
                        "20260619",
                        null,
                        null,
                        null,
                        "50000"
                ));
        var processing = bankingService.createDeposit(
                new CreateDepositRequest(account.bankAccountId(), new BigDecimal("50000")),
                "deposit-key-result-check",
                1L
        );
        when(openBankingClient.transferResult(any(OpenBankingTransferResultRequest.class), any()))
                .thenReturn(new OpenBankingTransferResultResponse(
                        "result-api-tran-id",
                        "20260619120100",
                        "A0000",
                        "success",
                        "1",
                        List.of(new OpenBankingTransferResultResponse.ResultItem(
                                "1",
                                "bank-tran-id",
                                "20260619",
                                "004",
                                "000",
                                "success",
                                "50000"
                        ))
                ));

        var completed = bankingService.checkTransferResult(processing.bankingTransferId(), 1L);

        assertThat(completed.status()).isEqualTo(BankingTransferStatus.COMPLETED);
        verify(walletClient).deposit(eq(10L), any(WalletBalanceChangeRequest.class), eq(true), any());
    }

    @Test
    void duplicateIdempotencyKeyWithSameRequestReturnsExistingResult() {
        linkOpenBankingToken();
        var account = bankingService.createBankAccount(new CreateBankAccountRequest("004", "1234567890", "Parent"), 1L);
        var request = new CreateDepositRequest(account.bankAccountId(), new BigDecimal("50000"));

        bankingService.createDeposit(request, "deposit-key-1", 1L);
        var response = bankingService.createDeposit(request, "deposit-key-1", 1L);

        assertThat(response.status()).isEqualTo(BankingTransferStatus.COMPLETED);
        assertThat(bankingTransferRepository.count()).isEqualTo(1);
        verify(walletClient, times(1)).deposit(eq(10L), any(WalletBalanceChangeRequest.class), eq(true), any());
    }

    @Test
    void duplicateIdempotencyKeyWithDifferentRequestFails() {
        linkOpenBankingToken();
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
        linkOpenBankingToken();
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
        linkOpenBankingToken();
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

    private void linkOpenBankingToken() {
        var authorize = bankingService.createAuthorizeUrl(1L, "parent");
        bankingService.handleOpenBankingCallback(new OpenBankingCallbackRequest("auth-code-1", authorize.state()), 1L);
    }
}
