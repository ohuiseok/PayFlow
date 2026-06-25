package com.payflow.ledger.service;

import com.payflow.ledger.entity.LedgerEntry;
import com.payflow.ledger.entity.LedgerEntryType;
import com.payflow.ledger.entity.LedgerSourceType;
import com.payflow.ledger.entity.TransferFailureEvent;
import com.payflow.ledger.dto.LedgerEntryResponse;
import com.payflow.ledger.dto.PaymentLedgerRequest;
import com.payflow.ledger.dto.TransferFailureEventResponse;
import com.payflow.ledger.event.TransferCompletedEvent;
import com.payflow.ledger.event.TransferFailedEvent;
import com.payflow.ledger.repository.LedgerEntryRepository;
import com.payflow.ledger.repository.TransferFailureEventRepository;
import com.payflow.ledger.support.error.BusinessException;
import com.payflow.ledger.support.error.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransferFailureEventRepository transferFailureEventRepository;

    @Transactional
    public LedgerEntry recordTransfer(TransferCompletedEvent event) {
        // 원장 기록은 송금 완료 이벤트를 기준으로 한 번만 만들어져야 한다.
        // Kafka는 장애/재시도 상황에서 같은 메시지를 다시 전달할 수 있으므로 transferId로 먼저 중복을 확인한다.
        return ledgerEntryRepository.findByTransferId(event.transferId())
                .orElseGet(() -> saveLedgerEntryIdempotently(event));
    }

    private LedgerEntry saveLedgerEntryIdempotently(TransferCompletedEvent event) {
        try {
            return ledgerEntryRepository.save(new LedgerEntry(
                    event.transferId(),
                    event.senderUserId(),
                    event.receiverUserId(),
                    event.amount()
            ));
        } catch (DataIntegrityViolationException exception) {
            return ledgerEntryRepository.findByTransferId(event.transferId())
                    .orElseThrow(() -> exception);
        }
    }

    @Transactional
    public LedgerEntryResponse recordPaymentCharge(PaymentLedgerRequest request) {
        return LedgerEntryResponse.from(ledgerEntryRepository
                .findBySourceTypeAndSourceId(request.sourceType(), request.sourceId())
                .orElseGet(() -> savePaymentLedgerEntryIdempotently(request)));
    }

    private LedgerEntry savePaymentLedgerEntryIdempotently(PaymentLedgerRequest request) {
        try {
            return ledgerEntryRepository.save(createPaymentLedgerEntry(request));
        } catch (DataIntegrityViolationException exception) {
            return ledgerEntryRepository.findBySourceTypeAndSourceId(request.sourceType(), request.sourceId())
                    .orElseThrow(() -> exception);
        }
    }

    private LedgerEntry createPaymentLedgerEntry(PaymentLedgerRequest request) {
        if (request.sourceType() == LedgerSourceType.TOSS_CHARGE
                && request.entryType() == LedgerEntryType.USER_WALLET_TOPUP) {
            return LedgerEntry.paymentCharge(request.sourceId(), request.userId(), request.amount());
        }
        if (request.sourceType() == LedgerSourceType.TOSS_CANCEL
                && request.entryType() == LedgerEntryType.PG_CANCEL) {
            return LedgerEntry.paymentCancel(request.sourceId(), request.userId(), request.amount());
        }
        throw new BusinessException(ErrorCode.INVALID_REQUEST, "Unsupported payment ledger source.");
    }

    @Transactional
    public TransferFailureEvent recordTransferFailure(TransferFailedEvent event) {
        return transferFailureEventRepository.findByTransferId(event.transferId())
                .orElseGet(() -> transferFailureEventRepository.save(new TransferFailureEvent(event)));
    }

    @Transactional(readOnly = true)
    public List<TransferFailureEventResponse> getTransferFailures(Long requestUserId) {
        // [C-4] 인증된 사용자가 관여한 송금 실패 이벤트만 반환한다. 타인 데이터 노출을 방지한다.
        return transferFailureEventRepository.findByUserId(requestUserId)
                .stream()
                .map(TransferFailureEventResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TransferFailureEventResponse getTransferFailure(Long transferId, Long requestUserId) {
        TransferFailureEvent event = transferFailureEventRepository.findByTransferId(transferId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        // [C-4] 해당 송금의 발신자 또는 수신자가 아니면 접근을 거부한다.
        if (!requestUserId.equals(event.getSenderUserId()) && !requestUserId.equals(event.getReceiverUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return TransferFailureEventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public TransferFailureEventResponse getTransferFailure(Long transferId) {
        TransferFailureEvent event = transferFailureEventRepository.findByTransferId(transferId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return TransferFailureEventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryResponse> getLedgerEntries(Long requestUserId) {
        // [C-4] 인증된 사용자가 관여한 원장 엔트리만 반환한다. 타인 데이터 노출을 방지한다.
        return ledgerEntryRepository.findTop100ByUserId(requestUserId)
                .stream()
                .map(LedgerEntryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public LedgerEntryResponse getLedgerEntry(Long entryId, Long requestUserId) {
        LedgerEntry entry = ledgerEntryRepository.findById(entryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        // [C-4] 해당 원장의 발신자 또는 수신자가 아니면 접근을 거부한다.
        if (!requestUserId.equals(entry.getSenderUserId()) && !requestUserId.equals(entry.getReceiverUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return LedgerEntryResponse.from(entry);
    }
}
