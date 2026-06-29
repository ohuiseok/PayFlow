package com.payflow.transfer.controller;

import com.payflow.transfer.dto.CreateTransferRequest;
import com.payflow.transfer.dto.TransferResponse;
import com.payflow.transfer.service.TransferService;
import com.payflow.transfer.support.error.BusinessException;
import com.payflow.transfer.support.error.ErrorCode;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @Value("${internal.secret:}")
    private String internalSecret;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse createTransfer(
            @Valid @RequestBody CreateTransferRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        // 송금 생성은 반드시 Idempotency-Key를 받는다.
        // 같은 요청을 재시도해도 송금이 중복 실행되지 않도록 서비스 계층에서 이 키와 request hash를 함께 검증한다.
        return transferService.createTransfer(request, idempotencyKey, requestUserId);
    }

    @GetMapping("/{transferId}")
    public TransferResponse getTransfer(
            @PathVariable Long transferId,
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        // 조회 요청자 ID를 함께 넘겨 보낸 사람/받은 사람만 송금 상세를 볼 수 있게 한다.
        return transferService.getTransfer(transferId, requestUserId);
    }

    @GetMapping("/by-idempotency-key")
    public TransferResponse getTransferByIdempotencyKey(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-User-Id") Long requestUserId
    ) {
        return transferService.getTransferByIdempotencyKey(idempotencyKey, requestUserId);
    }

    // [M-3] page, size 쿼리 파라미터로 페이지네이션을 지원한다.
    // 예: GET /transfers?page=0&size=20 (기본값: page=0, size=20)
    // Page<TransferResponse>를 직렬화하면 content, totalElements, totalPages, number 등이 함께 반환된다.
    @GetMapping
    public Page<TransferResponse> getTransfers(
            @RequestHeader("X-User-Id") Long requestUserId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return transferService.getTransfers(requestUserId, pageable);
    }

    @GetMapping("/compensations")
    public List<TransferResponse> getCompensations(
            @RequestHeader(value = "X-Internal-Secret", required = false) String requestInternalSecret
    ) {
        validateInternalRequest(requestInternalSecret);
        return transferService.getCompensations();
    }

    @GetMapping("/compensations/{transferId}")
    public TransferResponse getCompensation(
            @PathVariable Long transferId,
            @RequestHeader(value = "X-Internal-Secret", required = false) String requestInternalSecret
    ) {
        validateInternalRequest(requestInternalSecret);
        return transferService.getCompensation(transferId);
    }

    @PostMapping("/compensations/{transferId}/refund")
    public TransferResponse refundCompensation(
            @PathVariable Long transferId,
            @RequestHeader(value = "X-Internal-Secret", required = false) String requestInternalSecret
    ) {
        validateInternalRequest(requestInternalSecret);
        return transferService.refundCompensation(transferId);
    }

    private void validateInternalRequest(String requestInternalSecret) {
        if (!StringUtils.hasText(internalSecret) || !internalSecret.equals(requestInternalSecret)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
