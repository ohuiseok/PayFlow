# Transactional Outbox Pattern

## 핵심 개념

### 문제의 뿌리: 두 시스템 간 원자성 부재

결제 서비스에서 DB 저장과 메시지 발행은 본질적으로 서로 다른 시스템이다. DB 트랜잭션은 DB에만 원자성을 보장하고, Kafka 발행은 별도 네트워크 호출이다. 이 두 작업을 하나의 원자적 단위로 묶을 수 없다는 것이 핵심 문제다.

```
// 문제가 있는 패턴
@Transactional
public void completeTransfer(String transferId) {
    transfer.setStatus(COMPLETED);
    transferRepository.save(transfer);  // DB 커밋 성공
    // 여기서 서버가 죽으면?
    kafkaProducer.send("transfer-completed", event);  // 발행 누락!
}
```

DB 커밋 후 Kafka 발행 전에 프로세스가 죽으면 DB는 COMPLETED이지만 원장 이벤트는 영원히 발행되지 않는다. 반대로 Kafka 발행 후 DB 커밋 전에 죽으면 이벤트는 발행됐는데 DB에는 기록이 없다.

### Outbox Pattern의 동작 원리

해결책은 "메시지를 직접 Kafka로 보내는 대신, 먼저 DB에 저장하고, 별도 프로세스가 DB를 읽어 Kafka로 보내는" 것이다.

```
// Outbox Pattern 적용
@Transactional
public void completeTransfer(String transferId) {
    transfer.setStatus(COMPLETED);
    transferRepository.save(transfer);  // 비즈니스 상태 저장

    OutboxEvent event = OutboxEvent.builder()
        .eventType("TransferCompleted")
        .payload(serialize(transfer))
        .status(PENDING)
        .build();
    outboxRepository.save(event);  // 같은 트랜잭션에 이벤트 저장
    // DB 트랜잭션이 커밋되면 두 레코드가 함께 저장됨
    // 커밋 실패면 둘 다 롤백됨
}
```

Publisher는 별도 스레드나 스케줄러로 동작하며 PENDING 상태 이벤트를 읽어 Kafka로 발행한다.

```
// Outbox Publisher
@Scheduled(fixedDelay = 1000)
public void publishPendingEvents() {
    List<OutboxEvent> pendingEvents = outboxRepository.findByStatus(PENDING);
    for (OutboxEvent event : pendingEvents) {
        try {
            kafkaProducer.send(event.getTopic(), event.getPayload());
            event.setStatus(PUBLISHED);
            outboxRepository.save(event);
        } catch (Exception e) {
            event.setRetryCount(event.getRetryCount() + 1);
            outboxRepository.save(event);
        }
    }
}
```

### Outbox 테이블 구조

```sql
CREATE TABLE outbox_event (
    id          BIGSERIAL PRIMARY KEY,
    event_id    UUID NOT NULL UNIQUE,
    event_type  VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,  -- transferId, walletId 등
    payload     JSONB NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, PUBLISHED, FAILED
    retry_count INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    published_at TIMESTAMP
);

CREATE INDEX idx_outbox_status ON outbox_event(status, created_at);
```

### 발행 방법: Polling vs Change Data Capture (CDC)

**Polling 방식**: Publisher가 주기적으로 `SELECT ... WHERE status = 'PENDING'`을 실행한다. 구현이 단순하지만 DB 폴링 부하가 있고 발행 지연이 폴링 간격에 비례한다.

**CDC (Change Data Capture) 방식**: Debezium 같은 도구가 DB 바이너리 로그(binlog, WAL)를 읽어 변경 이벤트를 캡처한다. DB에 `outbox_event`가 INSERT되는 순간 변경 로그가 생성되고, Debezium이 이를 읽어 Kafka로 발행한다. 폴링보다 실시간에 가깝고 DB 부하도 낮지만 CDC 인프라 구성이 필요하다.

```
// Debezium을 사용하는 경우 outbox_event INSERT가 자동으로 Kafka로 전달됨
// 별도 Publisher 코드 불필요
```

### Outbox 발행이 중복을 만드는 이유와 Consumer 멱등성의 필요성

Publisher가 Kafka에 성공적으로 발행하고 `status=PUBLISHED`로 업데이트하기 전에 죽으면, 재시작 후 같은 이벤트를 다시 발행한다. 이것이 Outbox Pattern이 At-least-once를 보장하는 이유이고, Consumer가 멱등해야 하는 이유다.

```
Publisher 흐름:
1. SELECT PENDING events
2. Kafka.send(event)   ← 성공
3. UPDATE status=PUBLISHED  ← 여기서 죽으면
→ 재시작 후 같은 event를 다시 Kafka로 보냄
→ Consumer가 같은 이벤트를 두 번 받음
```

### Outbox 테이블 관리와 운영 고려사항

- **오래된 이벤트 정리**: PUBLISHED 상태 이벤트는 일정 기간 후 삭제하거나 아카이브한다. 이벤트가 계속 쌓이면 인덱스 성능이 저하된다.
- **실패 이벤트 감시**: FAILED 상태나 retry_count가 임계값을 초과한 이벤트는 알림을 발생시킨다.
- **발행 지연 모니터링**: `created_at`과 현재 시각의 차이가 너무 크면 Publisher가 멈춘 것이다.
- **분산 Publisher 중복 발행 방지**: 여러 Publisher 인스턴스가 같은 이벤트를 동시에 발행하지 않도록 `SELECT ... FOR UPDATE SKIP LOCKED`를 사용한다.

```sql
-- 분산 환경에서의 안전한 이벤트 조회
SELECT * FROM outbox_event
WHERE status = 'PENDING'
ORDER BY created_at
LIMIT 100
FOR UPDATE SKIP LOCKED;
```

### 흔한 오해와 함정

**"Outbox를 쓰면 Exactly-once가 보장된다"**: 아니다. Outbox는 At-least-once를 보장하고 유실을 막는다. 중복은 여전히 발생하므로 Consumer 멱등성이 반드시 함께 필요하다.

**"DB 트랜잭션 안에서 Kafka를 직접 발행하면 된다"**: 안전하지 않다. 트랜잭션이 롤백되더라도 Kafka 발행은 되돌릴 수 없다. Kafka 발행이 먼저 성공했는데 DB 롤백이 발생하면 이벤트가 거짓 데이터를 전달하는 상황이 된다.

## PayFlow 연결

송금 상태를 `COMPLETED`로 저장한 뒤 Kafka 발행이 실패하면 원장과 정산이 누락될 수 있다. 이를 막기 위해 `transfer-service`는 송금 상태 변경과 `outbox_event` 저장을 같은 트랜잭션으로 묶는다.

## 실무 포인트

- 비즈니스 상태와 Outbox 이벤트를 같은 트랜잭션에 저장한다.
- Publisher는 미발행 이벤트를 반복 조회한다.
- Kafka 발행 성공 후 Outbox 상태를 변경한다.
- 발행 중복이 가능하므로 Consumer는 멱등해야 한다.
- 오래된 실패 이벤트를 모니터링한다.

## 체크 질문

- Outbox Pattern은 어떤 불일치를 해결하는가
- Outbox를 써도 Consumer 멱등성이 필요한 이유는 무엇인가
- Outbox Publisher가 죽으면 시스템은 어떻게 복구할 수 있는가

## 실무 설계 보강

### 대표 장애 시나리오

송금 상태는 COMPLETED인데 Kafka 발행 전에 서버가 죽어 원장 이벤트가 없다.

### 잘못된 구현 예시

~~~text
DB 저장 후 바로 Kafka를 발행하면 충분하다고 생각한다.
~~~

### 좋은 구현 예시

~~~text
송금 상태와 outbox_event를 같은 트랜잭션에 저장하고 Publisher가 재발행한다.
~~~

### 대안과 선택 이유

서비스 간 REST 호출만으로 후속 처리를 연결하는 방식도 있지만, 원장이나 정산 서비스 장애가 송금 응답에 직접 영향을 준다. PayFlow는 Kafka와 Outbox로 시간적 결합을 낮추고, Consumer 멱등성과 DLQ로 중복/실패를 감당하는 방식이 더 적합하다.

### PayFlow에서 확인할 위치

transfer-service outbox table, outbox publisher, Kafka producer

### 면접에서 설명하기

Outbox는 DB와 메시지 브로커 사이의 원자성 gap을 줄이는 패턴이다.

### 관련 문서

04, 58, 60

## 질문의 질문으로 더 깊게 보기

### 이 문서에서 잡아야 할 CS 감각

이 구간의 핵심은 메시징 시스템이 비동기성과 신뢰성을 동시에 다룬다는 점이다. Kafka를 쓰면 결합도는 낮아지지만, 중복, 순서, 지연, 재처리 문제가 생긴다. 이벤트 기반 구조는 Consumer 멱등성이 있을 때 비로소 안전해진다.

### 질문의 질문

아래 질문들은 답을 외우기 위한 질문이 아니다. 하나씩 답하면서 "왜 그런 설계가 필요한지"를 설명하는 연습을 하기 위한 질문이다.

- DB 커밋은 성공했지만 Kafka 발행이 실패하면 어떤 데이터가 어긋나는가?
- Outbox Publisher가 같은 이벤트를 두 번 발행해도 왜 Consumer 멱등성이 필요한가?
- Outbox 테이블이 계속 쌓이면 송금 시스템에서 어떤 운영 문제가 생기는가?
- 이 개념을 적용하면 어떤 문제가 해결되지만, 어떤 비용이나 복잡도가 새로 생기는가?
- 장애가 발생했을 때 이 개념은 문제를 예방하는가, 감지하는가, 복구하는가?
- PayFlow 코드에서 이 개념을 확인하려면 어떤 서비스, 테이블, 로그, 테스트를 봐야 하는가?

### 답안 예시

<details>
<summary>답안 예시 펼치기</summary>

#### 핵심 답변

Transactional Outbox Pattern 개념은 PayFlow에서 다음 이유로 중요하다.

- Outbox는 PayFlow에서 DB 커밋과 Kafka 발행 사이의 틈 때문에 이벤트가 유실되는 문제를 막기 위해 필요하다.
- Kafka topic/partition/offset, outbox_event, processed_event, DLQ가 이벤트 처리의 기준이다.
- 송금 상태는 COMPLETED로 커밋됐지만 Kafka 발행 전에 서버가 죽으면 원장과 정산이 영원히 누락될 수 있다.
- 송금 상태와 outbox_event를 같은 DB 트랜잭션에 저장하고, Publisher가 미발행 이벤트를 반복 발행한다.
- 운영에서는 Kafka lag, rebalance 횟수, DLQ 메시지 수, Consumer 처리 시간, processed_event 중복 차단 건수를 본다.

#### 질문에 답하는 방식

좋은 답변은 용어 정의에서 멈추지 않고 다음 순서로 이어져야 한다.

1. 어떤 데이터나 요청을 보호하려는 개념인지 말한다.
2. 그 데이터의 진실의 원천이 어느 서비스에 있는지 말한다.
3. 장애, 중복, 동시성, 지연 중 어떤 상황에서 문제가 생기는지 설명한다.
4. 코드 레벨의 방어 수단과 운영 레벨의 확인 수단을 함께 말한다.

#### PayFlow 예시 답변

```text
Transactional Outbox Pattern 개념은 PayFlow에서 송금 상태 DB 커밋과 Kafka 발행 사이에서 이벤트가 사라지지 않게 하기 위해 필요하다.
이 개념이 없으면 COMPLETED 저장 후 Kafka 발행 전에 transfer-service가 죽으면 원장과 정산이 영원히 생성되지 않을 수 있다.
그래서 코드에서는 송금 상태와 outbox_event를 같은 트랜잭션에 저장하고 Publisher가 미발행 이벤트를 재발행하며,
운영에서는 Outbox 미발행/발행 실패, 발행 지연 시간, Consumer 처리 결과를 확인해야 한다.
```

#### 더 생각해볼 점

이 답안은 하나의 예시다. 실제 설계에서는 성능, 복잡도, 장애 복구 난이도 사이의 trade-off를 함께 봐야 한다. 특히 PayFlow처럼 돈을 다루는 시스템에서는 빠른 성공보다 명확한 실패, 추적 가능한 상태, 재처리 가능한 구조가 더 중요하다.

</details>

### PayFlow에 대입해보기

1. 이 개념이 가장 직접적으로 연결되는 PayFlow 서비스 하나를 고른다.
2. 그 서비스가 소유한 데이터의 "진실의 원천"이 무엇인지 적어본다.
3. 동시에 두 요청이 들어오거나, 네트워크가 끊기거나, 프로세스가 죽는 상황을 가정한다.
4. 그 상황에서 데이터가 깨지지 않으려면 어떤 제약조건, 트랜잭션, 락, 이벤트, 재처리 장치가 필요한지 설명한다.
5. 마지막으로 운영자가 문제를 발견할 수 있는 로그나 지표가 무엇인지 적어본다.

### 설명 연습

다음 문장을 자기 말로 완성해보자.

```text
Transactional Outbox Pattern 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.
