package com.payflow.banking.settlement;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentSettlementOutboxRepository extends JpaRepository<PaymentSettlementOutbox, Long> {
    boolean existsByEventId(String eventId);

    List<PaymentSettlementOutbox> findTop50ByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(
            Collection<SettlementOutboxStatus> statuses,
            int maxRetries
    );
}
