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
import com.payflow.banking.support.error.BusinessException;
import com.payflow.banking.support.error.ErrorCode;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
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

    @Value("${frontend.origin:http://localhost:19006}")
    private String frontendOrigin;

    @Value("${internal.secret:}")
    private String internalSecret;

    @GetMapping("/internal/has-account")
    public Map<String, Boolean> hasActiveBankAccount(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-Internal-Request", defaultValue = "false") boolean internalRequest,
            @RequestHeader(value = "X-Internal-Secret", required = false) String requestInternalSecret
    ) {
        validateInternalRequest(internalRequest, requestInternalSecret);
        return Map.of("hasBankAccount", bankingService.hasActiveBankAccount(userId));
    }

    private void validateInternalRequest(boolean internalRequest, String requestInternalSecret) {
        if (!internalRequest) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!StringUtils.hasText(internalSecret)) {
            return; // 내부 시크릿 미설정 시 개발 환경으로 간주하고 허용
        }
        if (!MessageDigest.isEqual(
                internalSecret.getBytes(StandardCharsets.UTF_8),
                (requestInternalSecret == null ? "" : requestInternalSecret).getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

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
    public OpenBankingAuthorizeUrlResponse createAuthorizeUrl(
            @RequestHeader("X-User-Id") Long requestUserId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole
    ) {
        return bankingService.createAuthorizeUrl(requestUserId, userRole);
    }

    @PostMapping("/openbanking/callback")
    public OpenBankingCallbackResponse handleOpenBankingCallback(
            @Valid @RequestBody OpenBankingCallbackRequest request,
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        return bankingService.handleOpenBankingCallback(request, requestUserId);
    }

    @GetMapping("/openbanking/callback")
    public ResponseEntity<Void> handleOpenBankingRedirect(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state
    ) {
        try {
            String userRole = bankingService.handleOpenBankingRedirect(code, state);
            return redirectToFrontend(userRole, "completed");
        } catch (RuntimeException exception) {
            return redirectToFrontend(null, "failed");
        }
    }

    private ResponseEntity<Void> redirectToFrontend(String userRole, String status) {
        String origin = frontendOrigin.endsWith("/")
                ? frontendOrigin.substring(0, frontendOrigin.length() - 1)
                : frontendOrigin;
        String path = "child".equalsIgnoreCase(userRole)
                ? "/child/bank-account"
                : "/parent/credit-charge";
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(origin + path + "?openbankingStatus=" + status))
                .build();
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
