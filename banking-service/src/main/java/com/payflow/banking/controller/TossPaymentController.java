package com.payflow.banking.controller;

import com.payflow.banking.dto.CreateTossChargeRequest;
import com.payflow.banking.dto.TossCancelRequest;
import com.payflow.banking.dto.TossChargeCreateResponse;
import com.payflow.banking.dto.TossChargeResponse;
import com.payflow.banking.dto.TossChargeSummaryResponse;
import com.payflow.banking.dto.TossConfirmRequest;
import com.payflow.banking.dto.TossOperationalSummaryResponse;
import com.payflow.banking.dto.TossPaymentWebhookRequest;
import com.payflow.banking.dto.TossWebhookResponse;
import com.payflow.banking.service.TossPaymentService;
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
@RequestMapping("/payments/toss")
@RequiredArgsConstructor
public class TossPaymentController {

    private final TossPaymentService tossPaymentService;

    @PostMapping("/charges")
    @ResponseStatus(HttpStatus.CREATED)
    public TossChargeCreateResponse createCharge(
            @Valid @RequestBody CreateTossChargeRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        return tossPaymentService.createCharge(request, idempotencyKey, requestUserId);
    }

    @GetMapping("/charges/{chargeId}")
    public TossChargeResponse getCharge(
            @PathVariable Long chargeId,
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        return tossPaymentService.getCharge(chargeId, requestUserId);
    }

    @PostMapping("/confirm")
    public TossChargeResponse confirm(
            @Valid @RequestBody TossConfirmRequest request,
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        return tossPaymentService.confirm(request, requestUserId);
    }

    @PostMapping("/webhook")
    public TossWebhookResponse webhook(
            @RequestBody TossPaymentWebhookRequest request,
            @RequestHeader(value = "X-Toss-Signature", required = false) String signature
    ) {
        return tossPaymentService.receiveWebhook(request, signature);
    }

    @GetMapping("/payments/{paymentKey}")
    public TossChargeResponse getPayment(
            @PathVariable String paymentKey,
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        return tossPaymentService.getPayment(paymentKey, requestUserId);
    }

    @PostMapping("/payments/{paymentKey}/cancel")
    public TossChargeResponse cancel(
            @PathVariable String paymentKey,
            @Valid @RequestBody TossCancelRequest request,
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        return tossPaymentService.cancel(paymentKey, request, requestUserId);
    }

    @GetMapping("/operations/summary")
    public TossOperationalSummaryResponse getOperationalSummary() {
        return tossPaymentService.getOperationalSummary();
    }

    @GetMapping("/operations/compensations")
    public List<TossChargeSummaryResponse> getCompensationRequiredCharges() {
        return tossPaymentService.getCompensationRequiredCharges();
    }

    @GetMapping("/operations/ledger-compensations")
    public List<TossChargeSummaryResponse> getLedgerCompensationRequiredCharges() {
        return tossPaymentService.getLedgerCompensationRequiredCharges();
    }

    @PostMapping("/charges/{chargeId}/compensate")
    public TossChargeResponse retryCompensation(
            @PathVariable Long chargeId,
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        return tossPaymentService.retryCompensation(chargeId, requestUserId);
    }

    @PostMapping("/charges/{chargeId}/ledger-compensate")
    public TossChargeResponse retryLedgerCompensation(
            @PathVariable Long chargeId,
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        return tossPaymentService.retryLedgerCompensation(chargeId, requestUserId);
    }
}
