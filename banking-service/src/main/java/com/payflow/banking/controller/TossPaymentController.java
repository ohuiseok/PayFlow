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
import com.payflow.banking.support.error.BusinessException;
import com.payflow.banking.support.error.ErrorCode;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/payments/toss")
@RequiredArgsConstructor
public class TossPaymentController {

    private final TossPaymentService tossPaymentService;

    @Value("${frontend.origin:http://localhost:19006}")
    private String frontendOrigin;

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

    @GetMapping("/success")
    public RedirectView successCallback(
            @RequestParam String paymentKey,
            @RequestParam String orderId,
            @RequestParam BigDecimal amount
    ) {
        TossChargeResponse response = tossPaymentService.confirmCallback(new TossConfirmRequest(paymentKey, orderId, amount));
        return redirectToCreditCharge("completed", response.chargeId(), null, null);
    }

    @GetMapping("/fail")
    public RedirectView failCallback(
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String message
    ) {
        Long chargeId = null;
        if (StringUtils.hasText(orderId)) {
            chargeId = tossPaymentService.failCallback(orderId, code, message).chargeId();
        }
        return redirectToCreditCharge("failed", chargeId, code, message);
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
    public TossOperationalSummaryResponse getOperationalSummary(
            // [C-5] 운영 API는 ROLE_ADMIN만 접근할 수 있다. 일반 사용자가 전체 운영 데이터에 접근하는 것을 방지한다.
            @RequestHeader("X-User-Role") String role
    ) {
        requireAdminRole(role);
        return tossPaymentService.getOperationalSummary();
    }

    @GetMapping("/operations/compensations")
    public List<TossChargeSummaryResponse> getCompensationRequiredCharges(
            @RequestHeader("X-User-Role") String role
    ) {
        requireAdminRole(role);
        return tossPaymentService.getCompensationRequiredCharges();
    }

    @GetMapping("/operations/ledger-compensations")
    public List<TossChargeSummaryResponse> getLedgerCompensationRequiredCharges(
            @RequestHeader("X-User-Role") String role
    ) {
        requireAdminRole(role);
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

    // [C-5] 운영 API 접근 권한을 검증한다. ROLE_ADMIN이 아니면 FORBIDDEN을 던진다.
    private void requireAdminRole(String role) {
        if (!"ROLE_ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private RedirectView redirectToCreditCharge(String status, Long chargeId, String code, String message) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(frontendOrigin)
                .path("/parent/credit-charge")
                .queryParam("tossStatus", status);
        if (chargeId != null) {
            builder.queryParam("chargeId", chargeId);
        }
        if (StringUtils.hasText(code)) {
            builder.queryParam("code", code);
        }
        if (StringUtils.hasText(message)) {
            builder.queryParam("message", message);
        }
        return new RedirectView(builder.build().encode().toUriString());
    }
}
