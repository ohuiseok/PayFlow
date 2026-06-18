package com.payflow.ledger.controller;

import com.payflow.ledger.dto.TransferFailureEventResponse;
import com.payflow.ledger.service.LedgerService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ledgers")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;

    @GetMapping("/transfer-failures")
    public List<TransferFailureEventResponse> getTransferFailures() {
        return ledgerService.getTransferFailures();
    }

    @GetMapping("/transfer-failures/{transferId}")
    public TransferFailureEventResponse getTransferFailure(@PathVariable Long transferId) {
        return ledgerService.getTransferFailure(transferId);
    }
}
