package com.payflow.transfer.outbox;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findTop50ByStatusInOrderByCreatedAtAsc(Collection<OutboxEventStatus> statuses);
}
