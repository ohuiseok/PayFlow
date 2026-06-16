package com.payflow.ledger.service;

import com.payflow.ledger.entity.LedgerEntry;
import com.payflow.ledger.event.TransferCompletedEvent;
import com.payflow.ledger.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional
    public LedgerEntry recordTransfer(TransferCompletedEvent event) {
        // 원장 기록은 송금 완료 이벤트를 기준으로 한 번만 만들어져야 한다.
        // Kafka는 장애/재시도 상황에서 같은 메시지를 다시 전달할 수 있으므로 transferId로 먼저 중복을 확인한다.
        return ledgerEntryRepository.findByTransferId(event.transferId())
                .orElseGet(() -> ledgerEntryRepository.save(new LedgerEntry(
                        event.transferId(),
                        event.senderUserId(),
                        event.receiverUserId(),
                        event.amount()
                )));
    }
}
