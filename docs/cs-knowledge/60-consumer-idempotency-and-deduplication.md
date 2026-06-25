# Consumer Idempotency And Deduplication

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

## 핵심 개념

### 멱등성의 수학적 정의와 시스템 적용

멱등성(Idempotency)은 수학에서 `f(f(x)) = f(x)`를 만족하는 성질이다. 시스템에서는 "동일한 작업을 여러 번 실행해도 결과가 한 번 실행한 것과 같다"는 의미다.

HTTP 메서드로 보면 `GET`, `PUT`, `DELETE`는 멱등하다. 같은 URL에 GET을 10번 해도 서버 상태가 변하지 않는다. 반면 `POST`는 기본적으로 멱등하지 않다. 같은 POST 요청을 10번 보내면 10개의 리소스가 생성될 수 있다.

결제 시스템의 Consumer는 기본적으로 멱등하지 않다. 원장 기록 생성, 잔액 차감, 정산 기록 생성은 같은 이벤트가 두 번 처리되면 두 번 실행된다. 이를 멱등하게 만드는 것이 Consumer 멱등성 설계의 목표다.

### 중복이 발생하는 구체적 시나리오

**시나리오 1: Consumer 재시작**
```
1. Consumer가 TransferCompleted 이벤트를 받음
2. 원장 기록 DB 저장 성공
3. Kafka offset 커밋 전에 Consumer 프로세스 종료
4. Consumer 재시작 후 같은 offset부터 다시 읽음
5. 같은 이벤트를 다시 처리 → 원장 중복 생성
```

**시나리오 2: Consumer Group Rebalance**
```
1. Consumer A가 Partition 0을 처리 중
2. Rebalance 발생 (다른 Consumer가 join/leave)
3. Partition 0이 Consumer B에게 재할당
4. Consumer B가 아직 커밋되지 않은 offset부터 다시 처리
5. Consumer A와 B가 같은 메시지를 모두 처리할 수 있음
```

**시나리오 3: Outbox Publisher 중복 발행**
```
1. Outbox Publisher가 이벤트를 Kafka에 성공적으로 발행
2. status=PUBLISHED 업데이트 전에 Publisher 재시작
3. 같은 이벤트를 다시 Kafka에 발행
4. Consumer가 같은 이벤트를 두 번 받음
```

### Deduplication 구현 방법

**방법 1: processed_event 테이블 기반**

```sql
CREATE TABLE processed_event (
    event_id    VARCHAR(100) PRIMARY KEY,
    event_type  VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

```java
@Transactional
public void handleTransferCompleted(TransferCompletedEvent event) {
    // 이미 처리된 이벤트인지 확인
    if (processedEventRepository.existsById(event.getEventId())) {
        log.info("Duplicate event, skipping: {}", event.getEventId());
        return;  // 중복이면 조용히 무시
    }

    // 비즈니스 처리
    ledgerService.createEntry(event);

    // 처리 기록 저장 (같은 트랜잭션)
    processedEventRepository.save(new ProcessedEvent(event.getEventId()));
}
```

비즈니스 처리와 처리 기록 저장이 같은 트랜잭션이어야 한다. 처리는 했는데 기록 저장에 실패하면 다음 중복 이벤트를 막지 못한다.

**방법 2: 비즈니스 키의 유니크 제약**

```sql
-- 원장 테이블에 유니크 제약
CREATE TABLE ledger_entry (
    id          BIGSERIAL PRIMARY KEY,
    transfer_id VARCHAR(100) NOT NULL,
    entry_type  VARCHAR(20) NOT NULL,   -- DEBIT, CREDIT
    amount      DECIMAL(19, 4) NOT NULL,
    UNIQUE (transfer_id, entry_type)    -- 같은 송금의 차변/대변은 한 번만
);
```

DB 레벨에서 중복 INSERT를 막으므로 애플리케이션 로직이 단순해진다. `INSERT ... ON CONFLICT DO NOTHING`으로 중복을 에러 없이 처리할 수 있다.

**방법 3: 두 방법의 조합**

```java
@Transactional
public void handleTransferCompleted(TransferCompletedEvent event) {
    try {
        ledgerService.createEntry(event);  // UNIQUE 제약으로 1차 방어
        processedEventRepository.save(new ProcessedEvent(event.getEventId()));
    } catch (DataIntegrityViolationException e) {
        // UNIQUE 제약 위반 = 이미 처리된 이벤트
        log.info("Duplicate event detected by DB constraint: {}", event.getEventId());
        // 정상 처리로 간주하고 offset 커밋
    }
}
```

### 처리 기록의 유효 기간 설계

`processed_event` 테이블이 무한정 커지면 조회 성능이 저하된다. 유효 기간을 어떻게 정해야 하는가?

- **너무 짧으면**: 오래된 중복 이벤트를 거르지 못한다. Outbox 재처리나 재해 복구로 오래된 이벤트가 다시 발행되면 막을 수 없다.
- **너무 길면**: 테이블이 커져서 조회 성능이 저하된다.

일반적으로 Kafka 메시지 보존 기간(기본 7일)보다 길게 유지하면 안전하다. 메시지가 더 이상 브로커에 없으면 중복으로 다시 올 수 없기 때문이다.

```sql
-- 오래된 처리 기록 정리 (배치 작업)
DELETE FROM processed_event
WHERE processed_at < NOW() - INTERVAL '30 days';
```

### 멱등하지 않은 외부 시스템 호출 처리

Consumer가 처리 중에 외부 API를 호출하는 경우, 그 API가 멱등하지 않으면 문제가 된다.

```java
// 위험한 패턴: 외부 SMS 발송 API 중복 호출
@Transactional
public void handleTransferCompleted(TransferCompletedEvent event) {
    if (processedEventRepository.existsById(event.getEventId())) {
        return;  // DB 처리는 막지만
    }
    ledgerService.createEntry(event);
    smsService.send(event.getUserPhone(), "송금 완료");  // 이미 보냈을 수도 있음
    processedEventRepository.save(new ProcessedEvent(event.getEventId()));
}
```

외부 API 호출은 `processed_event` 확인 이후에 배치하되, 외부 API 자체에 Idempotency-Key를 전달하는 것이 좋다.

### 흔한 오해와 함정

**"중복 이벤트를 에러로 처리하면 안 된다"**: 중복을 예외로 던지면 Consumer가 재시도를 반복하고 해당 파티션 처리가 멈출 수 있다. 중복은 예상된 상황이므로 조용히 성공으로 처리(skip)해야 한다.

**"SELECT 후 INSERT로는 race condition이 생긴다"**: 두 Consumer가 동시에 같은 `event_id`에 대해 `existsById=false`를 확인하고 둘 다 INSERT를 시도할 수 있다. DB UNIQUE 제약이 최종 방어선 역할을 한다. 이 경우 하나는 성공하고 하나는 `DataIntegrityViolationException`을 받는다.

**"처리와 기록 저장을 다른 트랜잭션으로 분리하면 안 된다"**: 처리는 성공했는데 기록 저장 트랜잭션이 실패하면 다음 중복을 막지 못한다.

## PayFlow 연결

`ledger-service`가 같은 `TransferCompleted` 이벤트를 두 번 받으면 원장이 두 번 기록될 수 있다. 이를 막기 위해 `eventId` 또는 `transferId`를 기준으로 처리 여부를 저장해야 한다.

DB 유니크 제약조건도 중요한 방어선이다.

## 실무 포인트

- 처리 완료 이벤트 ID를 저장한다.
- 비즈니스 키에 유니크 제약을 둔다.
- 중복 메시지는 성공 처리로 간주할 수 있다.
- 처리와 처리 기록 저장을 같은 트랜잭션에 둔다.
- 실패와 중복을 구분한다.

## 체크 질문

- Consumer가 같은 메시지를 두 번 받을 수 있는 이유는 무엇인가
- `processed_event` 테이블은 어떤 역할을 하는가
- 중복 메시지를 에러로 처리하면 어떤 문제가 생길 수 있는가

## 실무 설계 참고

### 대표 장애 시나리오

같은 TransferCompleted 이벤트가 두 번 소비되어 원장이 중복 기록된다.

### 잘못된 구현 예시

~~~text
Kafka가 한 번만 전달한다고 가정한다.
~~~

### 좋은 구현 예시

~~~text
eventId/transferId 처리 기록과 unique constraint로 Consumer를 멱등하게 만든다.
~~~

### 대안과 선택 이유

서비스 간 REST 호출만으로 후속 처리를 연결하는 방식도 있지만, 원장이나 정산 서비스 장애가 송금 응답에 직접 영향을 준다. PayFlow는 Kafka와 Outbox로 시간적 결합을 낮추고, Consumer 멱등성과 DLQ로 중복/실패를 감당하는 방식이 더 적합하다.

### PayFlow에서 확인할 위치

ledger-service consumer, processed_event table, ledger entry unique key

### 면접에서 설명하기

Consumer 멱등성은 이벤트 기반 결제 시스템의 필수 방어선이다.

### 관련 문서

40, 56, 58, 59

## 질문의 질문으로 더 깊게 보기

### 이 문서에서 잡아야 할 CS 감각

이 구간의 핵심은 메시징 시스템이 비동기성과 신뢰성을 동시에 다룬다는 점이다. Kafka를 쓰면 결합도는 낮아지지만, 중복, 순서, 지연, 재처리 문제가 생긴다. 이벤트 기반 구조는 Consumer 멱등성이 있을 때 비로소 안전해진다.

### 질문의 질문

아래 질문들은 답을 외우기 위한 질문이 아니다. 하나씩 답하면서 "왜 그런 설계가 필요한지"를 설명하는 연습을 하기 위한 질문이다.

- 같은 요청이 두 번 들어왔을 때 "두 번째 요청을 실패"시키는 것과 "기존 결과를 반환"하는 것은 어떻게 다른가?
- 처리 중 서버가 죽은 요청은 재시도 시 어떤 상태로 보일까?
- 멱등성 키의 유효 기간은 짧을수록 좋은가, 길수록 좋은가?
- 이 개념을 적용하면 어떤 문제가 해결되지만, 어떤 비용이나 복잡도가 새로 생기는가?
- 장애가 발생했을 때 이 개념은 문제를 예방하는가, 감지하는가, 복구하는가?
- PayFlow 코드에서 이 개념을 확인하려면 어떤 서비스, 테이블, 로그, 테스트를 봐야 하는가?

### 답안 예시

<details>
<summary>답안 예시 펼치기</summary>

#### 핵심 답변

Consumer Idempotency And Deduplication 개념은 PayFlow에서 다음 이유로 중요하다.

- 멱등성은 PayFlow에서 같은 송금 요청이 재시도되어도 실제 돈 이동은 한 번만 일어나게 만들기 위해 필요하다.
- Kafka topic/partition/offset, outbox_event, processed_event, DLQ가 이벤트 처리의 기준이다.
- 응답 타임아웃 후 클라이언트가 재시도하면 같은 송금이 두 번 처리되어 중복 차감이 발생할 수 있다.
- Idempotency-Key를 요청 내용 해시, 사용자, 처리 상태, 최종 응답과 함께 저장하고 유니크 제약으로 중복 생성을 막는다.
- 운영에서는 Kafka lag, rebalance 횟수, DLQ 메시지 수, Consumer 처리 시간, processed_event 중복 차단 건수를 본다.

#### 질문에 답하는 방식

좋은 답변은 용어 정의에서 멈추지 않고 다음 순서로 이어져야 한다.

1. 어떤 데이터나 요청을 보호하려는 개념인지 말한다.
2. 그 데이터의 진실의 원천이 어느 서비스에 있는지 말한다.
3. 장애, 중복, 동시성, 지연 중 어떤 상황에서 문제가 생기는지 설명한다.
4. 코드 레벨의 방어 수단과 운영 레벨의 확인 수단을 함께 말한다.

#### PayFlow 예시 답변

```text
Consumer Idempotency And Deduplication 개념은 PayFlow에서 같은 송금 완료 이벤트가 여러 번 와도 원장과 정산이 한 번만 반영되게 하기 위해 필요하다.
이 개념이 없으면 Consumer 재시작 후 같은 이벤트를 다시 읽어 차변/대변 원장이 중복 생성될 수 있다.
그래서 코드에서는 processed_event 테이블, eventId/transferId 유니크 제약, 처리와 기록의 같은 트랜잭션을 적용하고,
운영에서는 중복 이벤트 차단, unique violation, 처리 완료 이벤트 수를 확인해야 한다.
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
Consumer Idempotency And Deduplication 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.

