package com.payflow.ledger.controller;

import com.payflow.ledger.dto.LedgerEntryResponse;
import com.payflow.ledger.dto.PaymentLedgerRequest;
import com.payflow.ledger.dto.TransferFailureEventResponse;
import com.payflow.ledger.service.LedgerService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ledgers")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;

    @PostMapping("/internal/payment-charge")
    public LedgerEntryResponse recordPaymentCharge(@Valid @RequestBody PaymentLedgerRequest request) {
        return ledgerService.recordPaymentCharge(request);
    }

    @GetMapping("/entries")
    public List<LedgerEntryResponse> getLedgerEntries() {
        return ledgerService.getLedgerEntries();
    }

    @GetMapping("/entries/{entryId}")
    public LedgerEntryResponse getLedgerEntry(@PathVariable Long entryId) {
        return ledgerService.getLedgerEntry(entryId);
    }

    @GetMapping("/transfer-failures")
    public List<TransferFailureEventResponse> getTransferFailures() {
        return ledgerService.getTransferFailures();
    }

    @GetMapping("/transfer-failures/{transferId}")
    public TransferFailureEventResponse getTransferFailure(@PathVariable Long transferId) {
        return ledgerService.getTransferFailure(transferId);
    }
}
