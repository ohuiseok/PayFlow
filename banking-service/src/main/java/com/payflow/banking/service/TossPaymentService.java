package com.payflow.banking.service;

import com.payflow.banking.client.LedgerClient;
import com.payflow.banking.client.LedgerPaymentRecordRequest;
import com.payflow.banking.client.WalletBalanceChangeRequest;
import com.payflow.banking.client.WalletClient;
import com.payflow.banking.dto.CreateTossChargeRequest;
import com.payflow.banking.dto.TossCancelRequest;
import com.payflow.banking.dto.TossChargeCreateResponse;
import com.payflow.banking.dto.TossChargeResponse;
import com.payflow.banking.dto.TossChargeSummaryResponse;
import com.payflow.banking.dto.TossConfirmRequest;
import com.payflow.banking.dto.TossOperationalSummaryResponse;
import com.payflow.banking.dto.TossPaymentWebhookRequest;
import com.payflow.banking.dto.TossWebhookResponse;
import com.payflow.banking.entity.PaymentCharge;
import com.payflow.banking.entity.PaymentChargeStatus;
import com.payflow.banking.entity.TossPaymentEvent;
import com.payflow.banking.entity.TossPaymentOrder;
import com.payflow.banking.entity.TossPaymentStatus;
import com.payflow.banking.repository.PaymentChargeRepository;
import com.payflow.banking.repository.TossPaymentEventRepository;
import com.payflow.banking.repository.TossPaymentOrderRepository;
import com.payflow.banking.support.error.BusinessException;
import com.payflow.banking.support.error.ErrorCode;
import com.payflow.banking.toss.TossPaymentCancelResult;
import com.payflow.banking.toss.TossPaymentResult;
import com.payflow.banking.toss.TossPaymentsClient;
import com.payflow.banking.toss.TossPaymentsProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class TossPaymentService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000000");
    private static final String CHARGE_REFERENCE_TYPE = "TOSS_PAYMENT_CHARGE";
    private static final String CANCEL_REFERENCE_TYPE = "TOSS_PAYMENT_CANCEL";

    private final PaymentChargeRepository paymentChargeRepository;
    private final TossPaymentOrderRepository tossPaymentOrderRepository;
    private final TossPaymentEventRepository tossPaymentEventRepository;
    private final TossPaymentsClient tossPaymentsClient;
    private final TossPaymentsProperties tossPaymentsProperties;
    private final WalletClient walletClient;
    private final LedgerClient ledgerClient;

    @Value("${internal.secret:}")
    private String internalSecret;

    @Transactional
    public TossChargeCreateResponse createCharge(CreateTossChargeRequest request, String idempotencyKey, Long requestUserId) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        BigDecimal amount = normalizeAmount(request.amount());
        String orderName = StringUtils.hasText(request.orderName()) ? request.orderName().trim() : "PayFlow 크레딧 충전";
        String requestHash = sha256("TOSS:" + requestUserId + ":" + amount.toPlainString() + ":" + orderName);

        return paymentChargeRepository.findByIdempotencyKey(normalizedKey)
                .map(existing -> resolveExistingCreate(existing, requestHash))
                .orElseGet(() -> createNewCharge(requestUserId, amount, orderName, normalizedKey, requestHash));
    }

    @Transactional
    public TossChargeResponse confirm(TossConfirmRequest request, Long requestUserId) {
        BigDecimal amount = normalizeAmount(request.amount());
        TossPaymentOrder order = tossPaymentOrderRepository.findByTossOrderId(request.orderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_CHARGE_NOT_FOUND));
        PaymentCharge charge = paymentChargeRepository.findByIdAndUserId(order.getPaymentChargeId(), requestUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_CHARGE_NOT_FOUND));
        if (charge.getAmount().compareTo(amount) != 0) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_REQUEST_MISMATCH);
        }
        if (charge.getStatus().name().equals("COMPLETED")) {
            if (!charge.isLedgerRecorded()) {
                recordLedgerPaymentCharge(charge);
            }
            return TossChargeResponse.from(charge, order);
        }

        TossPaymentResult result = tossPaymentsClient.confirm(request.paymentKey(), request.orderId(), amount);
        applyResult(order, result);
        saveEvent(order, "APPROVE_RESPONSE", result.paymentKey(), null, result.status().name(), null, result.rawJson());

        if (result.status() != TossPaymentStatus.DONE) {
            charge.fail(result.status().name(), "Toss payment is not approved.");
            return TossChargeResponse.from(charge, order);
        }

        charge.markPaymentApproved();
        reflectChargeToWallet(charge);
        return TossChargeResponse.from(charge, order);
    }

    @Transactional(readOnly = true)
    public TossChargeResponse getCharge(Long chargeId, Long requestUserId) {
        PaymentCharge charge = paymentChargeRepository.findByIdAndUserId(chargeId, requestUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_CHARGE_NOT_FOUND));
        TossPaymentOrder order = tossPaymentOrderRepository.findByPaymentChargeId(charge.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_CHARGE_NOT_FOUND));
        return TossChargeResponse.from(charge, order);
    }

    @Transactional
    public TossChargeResponse getPayment(String paymentKey, Long requestUserId) {
        TossPaymentOrder order = tossPaymentOrderRepository.findByPaymentKey(paymentKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_CHARGE_NOT_FOUND));
        PaymentCharge charge = paymentChargeRepository.findByIdAndUserId(order.getPaymentChargeId(), requestUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_CHARGE_NOT_FOUND));
        TossPaymentResult result = tossPaymentsClient.getPayment(paymentKey);
        applyResult(order, result);
        saveEvent(order, "QUERY_RESPONSE", paymentKey, null, result.status().name(), null, result.rawJson());
        return TossChargeResponse.from(charge, order);
    }

    @Transactional
    public TossChargeResponse cancel(String paymentKey, TossCancelRequest request, Long requestUserId) {
        TossPaymentOrder order = tossPaymentOrderRepository.findByPaymentKey(paymentKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_CHARGE_NOT_FOUND));
        PaymentCharge charge = paymentChargeRepository.findByIdAndUserId(order.getPaymentChargeId(), requestUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_CHARGE_NOT_FOUND));
        TossPaymentCancelResult result = tossPaymentsClient.cancel(paymentKey, request.cancelReason(), request.cancelAmount());
        applyResult(order, result.payment());
        saveEvent(order, "CANCEL_RESPONSE", paymentKey, result.transactionKey(), result.payment().status().name(), null, result.payment().rawJson());
        try {
            if (charge.getWalletId() != null && result.canceledAmount() != null && result.canceledAmount().compareTo(BigDecimal.ZERO) > 0) {
                walletClient.withdraw(
                        charge.getWalletId(),
                        new WalletBalanceChangeRequest(result.canceledAmount(), CANCEL_REFERENCE_TYPE, paymentKey),
                        true,
                        internalSecret
                );
                recordLedgerPaymentCancel(charge, result.canceledAmount());
            }
            charge.cancel(result.payment().status() == TossPaymentStatus.PARTIAL_CANCELED);
        } catch (RuntimeException exception) {
            charge.requireCompensation("WALLET_CANCEL_FAILED", resolveMessage(exception));
        }
        return TossChargeResponse.from(charge, order);
    }

    @Transactional
    public TossWebhookResponse receiveWebhook(TossPaymentWebhookRequest request, String signature) {
        validateWebhookSignature(request, signature);
        String key = StringUtils.hasText(request.transactionKey())
                ? request.eventType() + ":" + request.paymentKey() + ":" + request.transactionKey()
                : request.eventType() + ":" + request.paymentKey() + ":" + request.status();
        String eventKey = sha256(key);
        if (tossPaymentEventRepository.existsByEventIdempotencyKey(eventKey)) {
            return new TossWebhookResponse(true, true);
        }
        TossPaymentOrder order = findWebhookOrder(request);
        if (StringUtils.hasText(request.status())) {
            order.applyPaymentResult(
                    request.paymentKey(),
                    null,
                    parseStatus(request.status()),
                    null,
                    null,
                    null,
                    null,
                    null,
                    toJson(request.payload())
            );
        }
        saveEvent(order, "WEBHOOK", request.paymentKey(), request.transactionKey(), request.status(), eventKey, toJson(request.payload()));
        return new TossWebhookResponse(true, false);
    }

    @Transactional(readOnly = true)
    public List<TossChargeSummaryResponse> getCompensationRequiredCharges() {
        return paymentChargeRepository.findTop50ByStatusOrderByUpdatedAtDesc(PaymentChargeStatus.COMPENSATION_REQUIRED)
                .stream()
                .map(TossChargeSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TossChargeSummaryResponse> getLedgerCompensationRequiredCharges() {
        return paymentChargeRepository.findTop50ByStatusAndLedgerRecordedFalseAndWalletIdIsNotNullOrderByUpdatedAtDesc(PaymentChargeStatus.COMPLETED)
                .stream()
                .map(TossChargeSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TossOperationalSummaryResponse getOperationalSummary() {
        return new TossOperationalSummaryResponse(
                paymentChargeRepository.countByStatus(PaymentChargeStatus.READY),
                paymentChargeRepository.countByStatus(PaymentChargeStatus.COMPLETED),
                paymentChargeRepository.countByStatus(PaymentChargeStatus.FAILED),
                paymentChargeRepository.countByStatus(PaymentChargeStatus.CANCELED)
                        + paymentChargeRepository.countByStatus(PaymentChargeStatus.PARTIAL_CANCELED),
                paymentChargeRepository.countByStatus(PaymentChargeStatus.COMPENSATION_REQUIRED),
                paymentChargeRepository.countByLedgerRecordedFalseAndWalletIdIsNotNullAndStatus(PaymentChargeStatus.COMPLETED)
        );
    }

    @Transactional
    public TossChargeResponse retryCompensation(Long chargeId, Long requestUserId) {
        PaymentCharge charge = paymentChargeRepository.findByIdAndUserId(chargeId, requestUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_CHARGE_NOT_FOUND));
        TossPaymentOrder order = tossPaymentOrderRepository.findByPaymentChargeId(charge.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_CHARGE_NOT_FOUND));
        if (charge.getStatus() != PaymentChargeStatus.COMPENSATION_REQUIRED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Payment charge is not compensation-required.");
        }
        if (order.getTossStatus() != TossPaymentStatus.DONE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Only approved Toss payments can be retried.");
        }
        try {
            charge.markWalletReflecting();
            var wallet = walletClient.getWalletByUserId(charge.getUserId(), true, internalSecret);
            var walletResponse = walletClient.deposit(
                    wallet.walletId(),
                    new WalletBalanceChangeRequest(charge.getAmount(), CHARGE_REFERENCE_TYPE, String.valueOf(charge.getId())),
                    true,
                    internalSecret
            );
            charge.complete(walletResponse.walletId(), null);
            recordLedgerPaymentCharge(charge);
        } catch (RuntimeException exception) {
            charge.requireCompensation("WALLET_DEPOSIT_FAILED", resolveMessage(exception));
            charge.recordCompensationFailure(resolveMessage(exception));
        }
        return TossChargeResponse.from(charge, order);
    }

    @Transactional
    public TossChargeResponse retryLedgerCompensation(Long chargeId, Long requestUserId) {
        PaymentCharge charge = paymentChargeRepository.findByIdAndUserId(chargeId, requestUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_CHARGE_NOT_FOUND));
        TossPaymentOrder order = tossPaymentOrderRepository.findByPaymentChargeId(charge.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_CHARGE_NOT_FOUND));
        if (charge.getStatus() != PaymentChargeStatus.COMPLETED || charge.getWalletId() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Only wallet-reflected completed charges can retry ledger compensation.");
        }
        if (charge.isLedgerRecorded()) {
            return TossChargeResponse.from(charge, order);
        }
        if (!recordLedgerPaymentCharge(charge)) {
            charge.recordLedgerCompensationFailure(charge.getLedgerFailureReason());
        }
        return TossChargeResponse.from(charge, order);
    }

    private TossPaymentOrder findWebhookOrder(TossPaymentWebhookRequest request) {
        if (StringUtils.hasText(request.orderId())) {
            return tossPaymentOrderRepository.findByTossOrderId(request.orderId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_CHARGE_NOT_FOUND));
        }
        if (StringUtils.hasText(request.paymentKey())) {
            return tossPaymentOrderRepository.findByPaymentKey(request.paymentKey())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_CHARGE_NOT_FOUND));
        }
        throw new BusinessException(ErrorCode.INVALID_REQUEST, "orderId or paymentKey is required.");
    }

    private void validateWebhookSignature(TossPaymentWebhookRequest request, String signature) {
        String secret = tossPaymentsProperties.webhookSecret();
        if (!StringUtils.hasText(secret)) {
            return;
        }
        if (!StringUtils.hasText(signature)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Toss webhook signature is required.");
        }
        String expected = sha256(secret + ":" + request.eventType() + ":" + request.paymentKey() + ":"
                + (StringUtils.hasText(request.transactionKey()) ? request.transactionKey() : request.status()));
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.trim().getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Toss webhook signature is invalid.");
        }
    }

    private TossChargeCreateResponse resolveExistingCreate(PaymentCharge charge, String requestHash) {
        if (!charge.getRequestHash().equals(requestHash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_REQUEST_MISMATCH);
        }
        TossPaymentOrder order = tossPaymentOrderRepository.findByPaymentChargeId(charge.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_CHARGE_NOT_FOUND));
        return TossChargeCreateResponse.from(charge, order);
    }

    private TossChargeCreateResponse createNewCharge(
            Long userId,
            BigDecimal amount,
            String orderName,
            String idempotencyKey,
            String requestHash
    ) {
        try {
            PaymentCharge charge = paymentChargeRepository.saveAndFlush(new PaymentCharge(userId, amount, idempotencyKey, requestHash));
            TossPaymentOrder order = tossPaymentOrderRepository.save(new TossPaymentOrder(
                    charge.getId(),
                    createTossOrderId(charge.getId(), userId),
                    orderName,
                    amount
            ));
            return TossChargeCreateResponse.from(charge, order);
        } catch (DataIntegrityViolationException exception) {
            return paymentChargeRepository.findByIdempotencyKey(idempotencyKey)
                    .map(existing -> resolveExistingCreate(existing, requestHash))
                    .orElseThrow(() -> exception);
        }
    }

    private void reflectChargeToWallet(PaymentCharge charge) {
        try {
            charge.markWalletReflecting();
            var wallet = walletClient.getWalletByUserId(charge.getUserId(), true, internalSecret);
            var walletResponse = walletClient.deposit(
                    wallet.walletId(),
                    new WalletBalanceChangeRequest(charge.getAmount(), CHARGE_REFERENCE_TYPE, String.valueOf(charge.getId())),
                    true,
                    internalSecret
            );
            charge.complete(walletResponse.walletId(), null);
            recordLedgerPaymentCharge(charge);
        } catch (RuntimeException exception) {
            charge.requireCompensation("WALLET_DEPOSIT_FAILED", resolveMessage(exception));
        }
    }

    private boolean recordLedgerPaymentCharge(PaymentCharge charge) {
        try {
            ledgerClient.recordPaymentCharge(
                    new LedgerPaymentRecordRequest(
                            "TOSS_CHARGE",
                            charge.getId(),
                            "USER_WALLET_TOPUP",
                            charge.getUserId(),
                            charge.getAmount()
                    ),
                    true,
                    internalSecret
            );
            charge.markLedgerRecorded("TOSS_CHARGE");
            return true;
        } catch (RuntimeException exception) {
            String message = resolveMessage(exception);
            charge.requireLedgerCompensation("TOSS_CHARGE", message);
            log.warn("Failed to record Toss charge ledger entry. chargeId={}", charge.getId(), exception);
            return false;
        }
    }

    private boolean recordLedgerPaymentCancel(PaymentCharge charge, BigDecimal canceledAmount) {
        try {
            ledgerClient.recordPaymentCharge(
                    new LedgerPaymentRecordRequest(
                            "TOSS_CANCEL",
                            charge.getId(),
                            "PG_CANCEL",
                            charge.getUserId(),
                            canceledAmount
                    ),
                    true,
                    internalSecret
            );
            charge.markLedgerRecorded("TOSS_CANCEL");
            return true;
        } catch (RuntimeException exception) {
            String message = resolveMessage(exception);
            charge.requireLedgerCompensation("TOSS_CANCEL", message);
            log.warn("Failed to record Toss cancel ledger entry. chargeId={}", charge.getId(), exception);
            return false;
        }
    }

    private void applyResult(TossPaymentOrder order, TossPaymentResult result) {
        order.applyPaymentResult(
                result.paymentKey(),
                result.method(),
                result.status(),
                result.totalAmount(),
                result.balanceAmount(),
                result.approvedAt(),
                result.receiptUrl(),
                result.checkoutUrl(),
                result.rawJson()
        );
    }

    private void saveEvent(
            TossPaymentOrder order,
            String eventType,
            String paymentKey,
            String transactionKey,
            String status,
            String eventIdempotencyKey,
            String payloadJson
    ) {
        tossPaymentEventRepository.save(new TossPaymentEvent(
                order.getId(),
                eventType,
                paymentKey,
                transactionKey,
                status,
                eventIdempotencyKey,
                payloadJson == null ? "{}" : payloadJson
        ));
    }

    private TossPaymentStatus parseStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return TossPaymentStatus.UNKNOWN;
        }
        try {
            return TossPaymentStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            return TossPaymentStatus.UNKNOWN;
        }
    }

    private String createTossOrderId(Long chargeId, Long userId) {
        return "payflow-charge-" + chargeId + "-" + userId;
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        return idempotencyKey.trim();
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null
                || amount.scale() > 0
                || amount.compareTo(BigDecimal.ONE) < 0
                || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "amount must be an integer from 1 to 10000000");
        }
        return amount.setScale(0, RoundingMode.UNNECESSARY);
    }

    private String toJson(Object value) {
        if (value == null) {
            return "{}";
        }
        if (value instanceof Map<?, ?> map && map.isEmpty()) {
            return "{}";
        }
        return value.toString();
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String resolveMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
