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
import org.springframework.web.bind.annotation.RequestHeader;
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
    public List<LedgerEntryResponse> getLedgerEntries(
            // [C-4] X-User-Id를 기준으로 본인이 관여한 원장 엔트리만 반환한다.
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        return ledgerService.getLedgerEntries(requestUserId);
    }

    @GetMapping("/entries/{entryId}")
    public LedgerEntryResponse getLedgerEntry(
            @PathVariable Long entryId,
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        return ledgerService.getLedgerEntry(entryId, requestUserId);
    }

    @GetMapping("/transfer-failures")
    public List<TransferFailureEventResponse> getTransferFailures(
            // [C-4] X-User-Id를 기준으로 본인이 관여한 송금 실패 이벤트만 반환한다.
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        return ledgerService.getTransferFailures(requestUserId);
    }

    @GetMapping("/transfer-failures/{transferId}")
    public TransferFailureEventResponse getTransferFailure(
            @PathVariable Long transferId,
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        return ledgerService.getTransferFailure(transferId, requestUserId);
    }
}
