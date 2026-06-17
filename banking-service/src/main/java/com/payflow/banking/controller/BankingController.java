package com.payflow.banking.controller;

import com.payflow.banking.dto.BankAccountResponse;
import com.payflow.banking.dto.BankingTransferResponse;
import com.payflow.banking.dto.CreateBankAccountRequest;
import com.payflow.banking.dto.CreateDepositRequest;
import com.payflow.banking.service.BankingService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping("/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public BankingTransferResponse createDeposit(
            @Valid @RequestBody CreateDepositRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        return bankingService.createDeposit(request, idempotencyKey, requestUserId);
    }

    @GetMapping("/transfers/{bankingTransferId}")
    public BankingTransferResponse getTransfer(
            @PathVariable Long bankingTransferId,
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        return bankingService.getTransfer(bankingTransferId, requestUserId);
    }
}
