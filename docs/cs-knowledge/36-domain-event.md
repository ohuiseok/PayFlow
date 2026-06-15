# Domain Event

## 핵심 개념

### 도메인 이벤트란 무엇인가

도메인 이벤트는 도메인에서 의미 있는 일이 발생했음을 표현하는 메시지다. "명령(Command)"은 무언가를 해달라는 요청이지만, "이벤트(Event)"는 이미 발생한 사실의 기록이다.

```text
명령 (Command): "10,000원 송금해"
이벤트 (Event): "10,000원 송금이 완료되었다"
```

이름도 이 차이를 반영해야 한다. 명령은 현재형(`ProcessTransfer`), 이벤트는 과거형(`TransferCompleted`)으로 표현한다.

### 도메인 이벤트를 사용하는 이유

**서비스 결합도 감소**

도메인 이벤트 없이 결합된 구조에서는 `transfer-service`가 `ledger-service`의 API를 직접 호출해야 한다.

```text
// 강한 결합 구조
transfer-service --[HTTP POST]--> ledger-service
transfer-service --[HTTP POST]--> settlement-service

// transfer-service가 ledger-service와 settlement-service 주소와
// API 스펙을 직접 알아야 한다
```

이벤트를 사용하면 `transfer-service`는 이벤트만 발행하고, 누가 소비하는지 알 필요가 없다.

```text
// 이벤트 기반 구조
transfer-service --> [TransferCompleted 이벤트 발행] --> Kafka
                                                         ↓
                                             ledger-service (소비)
                                             settlement-service (소비)
```

새로운 서비스가 `TransferCompleted` 이벤트를 소비하고 싶어도 `transfer-service`를 수정할 필요가 없다.

**사실 기반 통신**

이벤트는 이미 발생한 사실이다. Consumer는 그 사실을 받아 자신의 책임을 수행한다. 이벤트를 명령처럼 "이것을 해라"는 의도로 설계하면, 발행자와 소비자 사이에 결합이 생기고 이벤트 스키마가 비즈니스 로직에 종속된다.

```java
// 나쁜 예: 이벤트를 명령처럼 사용
class TransferEvent {
    String action = "RECORD_LEDGER";  // Consumer에게 무엇을 하라고 지시
    long debitAmount;
    long creditAmount;
}

// 좋은 예: 이벤트는 사실만 표현
class TransferCompletedEvent {
    String transferId;    // 어떤 송금인지
    String senderId;      // 누가 보냈는지
    String receiverId;    // 누가 받았는지
    long amount;          // 얼마를
    String currency;      // 어떤 통화로
    Instant occurredAt;   // 언제 완료됐는지
}
```

### 이벤트 스키마 설계

도메인 이벤트에는 Consumer가 자신의 역할을 수행하는 데 필요한 최소한의 정보만 담는다. 너무 많은 정보를 담으면 발행자의 내부 구현이 이벤트에 노출되어 스키마 변경이 어려워진다.

필수 포함 항목:

```json
{
  "eventId": "evt-550e8400-e29b-41d4-a716",
  "eventType": "TransferCompleted",
  "occurredAt": "2026-05-30T09:00:00Z",
  "aggregateId": "transfer-123",
  "version": 1,
  "payload": {
    "transferId": "transfer-123",
    "senderId": "user-A",
    "receiverId": "user-B",
    "amount": 10000,
    "currency": "KRW"
  }
}
```

- `eventId`: Consumer의 중복 처리 방지를 위한 고유 ID
- `occurredAt`: 이벤트가 발생한 실제 시각 (처리 시각이 아님)
- `version`: 스키마 버전 관리를 위해 필요
- `aggregateId`: 어떤 도메인 객체에 대한 이벤트인지

### 이벤트 스키마 버전 관리

서비스가 발전하면 이벤트 스키마가 변경될 수 있다. Consumer들은 이 변경에 맞게 업데이트해야 한다. 하지만 모든 Consumer를 동시에 배포하기 어렵기 때문에 하위 호환성을 유지하는 전략이 필요하다.

```text
// 안전한 변경: 필드 추가 (기존 Consumer는 새 필드를 무시)
v1: { transferId, amount }
v2: { transferId, amount, fee }  // fee 추가는 하위 호환

// 위험한 변경: 필드 삭제 또는 타입 변경
v1: { transferId, amount }
v2: { transferId }  // amount 삭제는 기존 Consumer 오류 발생
```

이벤트 버전을 명시하고, Consumer가 처리할 수 없는 버전의 이벤트를 만나면 DLQ(Dead Letter Queue)로 보내 별도 처리하는 것이 안전하다.

### Transactional Outbox와의 관계

이벤트를 발행할 때 중요한 문제가 있다. DB 트랜잭션을 커밋한 후 이벤트를 발행하려는데, 발행 직전에 프로세스가 죽으면 이벤트가 유실된다.

```text
// 위험한 구조
1. DB 트랜잭션 커밋 (송금 완료)
2. Kafka에 이벤트 발행 ← 여기서 죽으면 이벤트 유실
```

Transactional Outbox 패턴은 이 문제를 해결한다. 이벤트 발행 기록을 DB 트랜잭션 안에 함께 저장하고, 별도 프로세스가 이를 읽어 Kafka로 발행한다.

```text
// Transactional Outbox 구조
DB 트랜잭션 (원자적) {
  INSERT INTO transfers (송금 완료 기록)
  INSERT INTO outbox_events (발행할 이벤트 기록)
}

별도 Relay 프로세스:
  SELECT * FROM outbox_events WHERE published = false
  → Kafka 발행
  → UPDATE outbox_events SET published = true
```

### Consumer의 멱등성

이벤트는 네트워크 문제나 Consumer 재시작으로 인해 같은 이벤트가 여러 번 도달할 수 있다. Consumer는 같은 이벤트를 두 번 처리해도 결과가 달라지지 않도록 멱등하게 설계해야 한다.

```java
// 멱등한 Consumer 예시 (eventId로 중복 확인)
@KafkaListener(topics = "transfer-completed")
public void handle(TransferCompletedEvent event) {
    if (ledgerRepository.existsByEventId(event.getEventId())) {
        log.info("이미 처리된 이벤트: {}", event.getEventId());
        return;
    }
    ledgerRepository.save(new LedgerEntry(event));
}
```

### 흔한 오해

**이벤트에 모든 정보를 담아야 Consumer가 편하다?**

이벤트에 너무 많은 정보를 담으면 발행자의 내부 구조가 이벤트 스키마에 노출된다. 스키마를 변경할 때 모든 Consumer에 영향을 준다. Consumer가 추가 정보가 필요하다면 이벤트를 받은 후 소유 서비스 API를 호출해 조회하는 방식이 더 유연하다.

**이벤트를 발행하면 Consumer가 반드시 처리한다?**

이벤트는 발행되었다고 반드시 처리되는 것이 아니다. Consumer 장애, 네트워크 문제, 스키마 불일치로 처리되지 않을 수 있다. 중요한 이벤트는 처리 실패를 모니터링하고 DLQ에서 재처리하는 구조가 필요하다.

## PayFlow 연결

PayFlow에서는 송금 완료 후 원장 기록과 정산 처리가 이어져야 한다. `transfer-service`가 `TransferCompleted` 이벤트를 발행하면 `ledger-service`와 `settlement-service`가 이를 소비할 수 있다.

이벤트를 사용하면 송금 처리와 후속 처리를 느슨하게 연결할 수 있다.

## 실무 포인트

- 이벤트 이름은 과거형 사실로 표현한다.
- 이벤트에는 Consumer가 필요한 최소 데이터를 담는다.
- 이벤트 스키마 변경은 하위 호환성을 고려한다.
- 이벤트 ID와 발생 시간을 포함한다.
- 같은 이벤트를 중복 처리해도 안전하게 만든다.

## 체크 질문

- 도메인 이벤트와 명령의 차이는 무엇인가
- `TransferCompleted` 이벤트가 필요한 이유는 무엇인가
- 이벤트에 `eventId`가 필요한 이유는 무엇인가

## 실무 설계 참고

### 대표 장애 시나리오

송금 완료라는 사실을 원장과 정산에 전달해야 하는데 서비스 간 결합이 커진다.

### 잘못된 구현 예시

~~~text
이벤트를 명령처럼 설계해 Consumer에게 무엇을 하라고 지시한다.
~~~

### 좋은 구현 예시

~~~text
TransferCompleted처럼 이미 발생한 사실을 이벤트로 발행하고 Consumer가 자기 책임을 수행한다.
~~~

### 대안과 선택 이유

분산 트랜잭션이나 모든 후속 처리를 동기 호출로 묶는 방식도 있지만, 서비스 하나의 장애가 전체 송금 실패로 번지기 쉽다. PayFlow는 로컬 트랜잭션, Saga, 도메인 이벤트, Outbox, 멱등성을 조합해 부분 실패를 상태로 남기고 복구하는 방식을 선택하는 것이 적합하다.

### PayFlow에서 확인할 위치

transfer event model, Kafka topic, ledger/settlement consumer

### 면접에서 설명하기

도메인 이벤트는 비즈니스 사실의 기록이다. 명령과 사실을 구분해야 결합도가 낮아진다.

### 관련 문서

37, 53, 59

## 질문의 질문으로 더 깊게 보기

### 이 문서에서 잡아야 할 CS 감각

이 구간의 핵심은 여러 서비스의 작업을 하나의 로컬 트랜잭션처럼 다룰 수 없다는 사실이다. 그래서 Saga, Domain Event, Outbox, 멱등성이 등장한다. 이들은 모두 "부분 실패를 어떻게 추적하고 복구할 것인가"에 대한 답이다.

### 질문의 질문

아래 질문들은 답을 외우기 위한 질문이 아니다. 하나씩 답하면서 "왜 그런 설계가 필요한지"를 설명하는 연습을 하기 위한 질문이다.

- Domain Event 개념이 없다면 PayFlow에서 가장 먼저 어떤 장애가 생기는가?
- 이 개념은 정확성, 성능, 보안, 운영성 중 무엇을 가장 크게 개선하는가?
- 반대로 이 개념을 잘못 적용하면 어떤 복잡도가 추가되는가?
- 이 개념을 적용하면 어떤 문제가 해결되지만, 어떤 비용이나 복잡도가 새로 생기는가?
- 장애가 발생했을 때 이 개념은 문제를 예방하는가, 감지하는가, 복구하는가?
- PayFlow 코드에서 이 개념을 확인하려면 어떤 서비스, 테이블, 로그, 테스트를 봐야 하는가?

### 답안 예시

<details>
<summary>답안 예시 펼치기</summary>

#### 핵심 답변

Domain Event 개념은 PayFlow에서 다음 이유로 중요하다.

- Saga, 이벤트, 멱등성 개념은 PayFlow에서 여러 서비스에 걸친 송금 흐름을 부분 실패에도 복구 가능하게 만들기 위해 필요하다.
- transfer-service의 상태 머신과 outbox_event, wallet-service의 잔액 변경, ledger-service의 처리 이벤트가 기준이다.
- 출금 성공 후 입금 실패, 송금 완료 후 이벤트 발행 실패, 같은 요청 재시도가 대표적인 위험이다.
- 명확한 상태 전이, 보상 트랜잭션, Domain Event, Outbox, Idempotency-Key, Consumer 멱등성으로 방어한다.
- 운영에서는 COMPENSATION_REQUIRED 건수, Outbox 실패 건수, idempotency hit 수, 보상 실패 건수, 상태별 송금 수를 본다.

#### 질문에 답하는 방식

좋은 답변은 용어 정의에서 멈추지 않고 다음 순서로 이어져야 한다.

1. 어떤 데이터나 요청을 보호하려는 개념인지 말한다.
2. 그 데이터의 진실의 원천이 어느 서비스에 있는지 말한다.
3. 장애, 중복, 동시성, 지연 중 어떤 상황에서 문제가 생기는지 설명한다.
4. 코드 레벨의 방어 수단과 운영 레벨의 확인 수단을 함께 말한다.

#### PayFlow 예시 답변

```text
Domain Event 개념은 PayFlow에서 송금 완료라는 도메인 사실을 원장과 정산 서비스에 느슨하게 전달하기 위해 필요하다.
이 개념이 없으면 transfer-service가 ledger-service의 내부 API와 강하게 결합되어 원장 장애가 송금 응답 장애로 번질 수 있다.
그래서 코드에서는 TransferCompleted 같은 과거형 이벤트와 eventId, 발생 시각, 최소 payload를 정의하고,
운영에서는 이벤트 발행 수, 소비 성공 수, 스키마 호환성 오류를 확인해야 한다.
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
Domain Event 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.
