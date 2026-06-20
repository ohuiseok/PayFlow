package com.payflow.banking.controller;

import com.payflow.banking.dto.BankAccountResponse;
import com.payflow.banking.dto.BankingTransferResponse;
import com.payflow.banking.dto.CreateBankAccountRequest;
import com.payflow.banking.dto.CreateDepositRequest;
import com.payflow.banking.dto.CreateWithdrawalRequest;
import com.payflow.banking.dto.OpenBankingAttemptResponse;
import com.payflow.banking.dto.OpenBankingAuthorizeUrlResponse;
import com.payflow.banking.dto.OpenBankingCallbackRequest;
import com.payflow.banking.dto.OpenBankingCallbackResponse;
import com.payflow.banking.openbanking.OpenBankingDepositTransferRequest;
import com.payflow.banking.openbanking.OpenBankingRealNameInquiryRequest;
import com.payflow.banking.openbanking.OpenBankingReceiveInquiryRequest;
import com.payflow.banking.service.BankingService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bank")
@RequiredArgsConstructor
public class BankingController {

    private final BankingService bankingService;

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public BankAccountResponse createBankAccount(
            @Valid @RequestBody CreateBankAccountRequest request,
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        return bankingService.createBankAccount(request, requestUserId);
    }

    @GetMapping("/accounts")
    public List<BankAccountResponse> getBankAccounts(@RequestHeader("X-User-Id") Long requestUserId) {
        return bankingService.getBankAccounts(requestUserId);
    }

    @GetMapping("/openbanking/authorize-url")
    public OpenBankingAuthorizeUrlResponse createAuthorizeUrl(@RequestHeader("X-User-Id") Long requestUserId) {
        return bankingService.createAuthorizeUrl(requestUserId);
    }

    @PostMapping("/openbanking/callback")
    public OpenBankingCallbackResponse handleOpenBankingCallback(
            @Valid @RequestBody OpenBankingCallbackRequest request,
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        return bankingService.handleOpenBankingCallback(request, requestUserId);
    }

    @GetMapping("/openbanking/callback")
    public OpenBankingCallbackResponse handleOpenBankingRedirect(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state
    ) {
        return bankingService.handleOpenBankingRedirect(code, state);
    }

    @PostMapping("/openbanking/accounts/sync")
    public List<BankAccountResponse> syncOpenBankingAccounts(
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        return bankingService.syncOpenBankingAccounts(requestUserId);
    }

    @PostMapping("/openbanking/attempts/real-name")
    public OpenBankingAttemptResponse attemptRealNameInquiry(@RequestBody OpenBankingRealNameInquiryRequest request) {
        return bankingService.attemptRealNameInquiry(request);
    }

    @PostMapping("/openbanking/attempts/receive")
    public OpenBankingAttemptResponse attemptReceiveInquiry(@RequestBody OpenBankingReceiveInquiryRequest request) {
        return bankingService.attemptReceiveInquiry(request);
    }

    @PostMapping("/openbanking/attempts/deposit-transfer")
    public OpenBankingAttemptResponse attemptDepositTransfer(@RequestBody OpenBankingDepositTransferRequest request) {
        return bankingService.attemptDepositTransfer(request);
    }

    @PostMapping("/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public BankingTransferResponse createDeposit(
            @Valid @RequestBody CreateDepositRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        return bankingService.createDeposit(request, idempotencyKey, requestUserId);
    }

    @PostMapping("/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    public BankingTransferResponse createWithdrawal(
            @Valid @RequestBody CreateWithdrawalRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        return bankingService.createWithdrawal(request, idempotencyKey, requestUserId);
    }

    @GetMapping("/transfers/{bankingTransferId}")
    public BankingTransferResponse getTransfer(
            @PathVariable Long bankingTransferId,
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        return bankingService.getTransfer(bankingTransferId, requestUserId);
    }

    @PostMapping("/transfers/{bankingTransferId}/result-check")
    public BankingTransferResponse checkTransferResult(
            @PathVariable Long bankingTransferId,
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        return bankingService.checkTransferResult(bankingTransferId, requestUserId);
    }

    @PostMapping("/transfers/{bankingTransferId}/compensate")
    public BankingTransferResponse compensateWithdrawal(
            @PathVariable Long bankingTransferId,
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        return bankingService.compensateWithdrawal(bankingTransferId, requestUserId);
    }
}
