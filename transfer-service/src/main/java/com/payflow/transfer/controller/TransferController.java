package com.payflow.transfer.controller;

import com.payflow.transfer.dto.CreateTransferRequest;
import com.payflow.transfer.dto.TransferResponse;
import com.payflow.transfer.service.TransferService;
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
@RequestMapping("/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

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

    @GetMapping
    public List<TransferResponse> getTransfers(@RequestHeader("X-User-Id") Long requestUserId) {
        // 목록 조회도 인증된 사용자 기준으로 제한한다.
        // 컨트롤러는 헤더를 전달하고, 실제 필터링 조건은 서비스/리포지토리에 둔다.
        return transferService.getTransfers(requestUserId);
    }
}
