# Event Driven Architecture

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

## 핵심 개념

### Event Driven Architecture란

Event Driven Architecture(EDA)는 시스템 컴포넌트들이 직접 서로를 호출하는 대신, 이벤트를 통해 간접적으로 소통하는 아키텍처다. 이벤트를 발행하는 쪽(Producer)과 소비하는 쪽(Consumer)이 느슨하게 결합된다.

단순한 요청-응답(Request-Response) 구조와의 차이는 다음과 같다.

```text
// 요청-응답 구조
transfer-service --[HTTP 직접 호출]--> ledger-service
                                       settlement-service

// EDA 구조
transfer-service --> [이벤트 발행] --> 메시지 브로커(Kafka)
                                         ↓         ↓
                                  ledger-service  settlement-service
```

EDA에서 `transfer-service`는 이벤트를 발행한 후 다음 작업으로 넘어간다. `ledger-service`가 다운되어도 `transfer-service`는 영향받지 않는다. `ledger-service`가 복구되면 쌓인 이벤트를 처리한다.

### 핵심 구성 요소

**Producer (이벤트 발행자)**

비즈니스 사건이 발생했을 때 이벤트를 생성해 브로커로 전달한다. 누가 이 이벤트를 소비하는지 알 필요 없다.

**Message Broker (메시지 브로커)**

이벤트를 저장하고 Consumer에게 전달하는 중간 인프라다. Kafka, RabbitMQ, AWS SQS/SNS가 대표적이다. Kafka는 이벤트를 디스크에 보존(retention)하므로 Consumer가 나중에 재처리할 수 있다.

**Consumer (이벤트 소비자)**

브로커에서 이벤트를 읽어 자신의 비즈니스 로직을 실행한다. Consumer Group을 사용해 여러 인스턴스가 이벤트를 분산 처리할 수 있다.

### Kafka의 핵심 개념

Kafka는 EDA의 핵심 인프라로 자주 사용된다. 핵심 개념을 이해해야 EDA를 제대로 설계할 수 있다.

**Topic과 Partition**

Topic은 이벤트를 분류하는 채널이다. 각 Topic은 여러 Partition으로 나뉘어 병렬 처리가 가능하다. Partition 내에서는 이벤트 순서가 보장된다.

```text
Topic: transfer-events
  Partition 0: [이벤트A, 이벤트D, 이벤트G ...]  (순서 보장)
  Partition 1: [이벤트B, 이벤트E, 이벤트H ...]  (순서 보장)
  Partition 2: [이벤트C, 이벤트F, 이벤트I ...]  (순서 보장)
```

같은 사용자의 이벤트를 순서대로 처리해야 한다면, 사용자 ID를 파티션 키로 사용해 같은 사용자의 이벤트가 항상 같은 Partition에 들어가도록 한다.

**Consumer Group과 오프셋**

Consumer Group은 같은 이벤트를 공동으로 소비하는 Consumer 집합이다. 같은 Group 내에서는 각 Partition의 이벤트가 하나의 Consumer에게만 전달된다. 다른 Consumer Group은 같은 이벤트를 독립적으로 소비할 수 있다.

```text
Topic Partition 0의 이벤트:

ledger-service Consumer Group:
  consumer-1이 Partition 0 소비 (offset 0, 1, 2, ...)

settlement-service Consumer Group:
  consumer-A도 Partition 0 독립적으로 소비 (offset 0, 1, 2, ...)
```

오프셋(offset)은 Consumer가 어디까지 읽었는지 추적한다. Consumer가 재시작되면 마지막으로 커밋한 오프셋부터 다시 읽는다.

### 이벤트 전달 보장 수준

EDA에서 이벤트 전달을 어떻게 보장할지 결정해야 한다.

**At-most-once (최대 한 번)**

이벤트가 전달되지 않을 수 있지만 중복 전달은 없다. 일부 유실을 허용할 수 있는 로그, 통계 같은 경우에 적합하다.

**At-least-once (최소 한 번)**

이벤트가 반드시 전달되지만 중복 전달이 있을 수 있다. Consumer가 처리 완료 전에 죽으면 재시작 후 같은 이벤트를 다시 받는다. Consumer는 반드시 멱등하게 설계해야 한다. 결제 시스템에서 가장 현실적인 선택이다.

**Exactly-once (정확히 한 번)**

이론적으로 이상적이지만 구현이 어렵고 성능 비용이 크다. Kafka의 트랜잭션 프로듀서와 멱등 컨슈머를 함께 사용하면 제한적으로 달성할 수 있다.

### 이벤트 순서 보장과 한계

Kafka는 같은 Partition 내에서만 순서를 보장한다. 여러 Partition에 걸친 순서는 보장되지 않는다.

```text
// 문제 시나리오
이벤트 1: Transfer 생성 → Partition 0
이벤트 2: Transfer 완료 → Partition 1

ledger-service가 Partition 1의 "완료" 이벤트를
Partition 0의 "생성" 이벤트보다 먼저 받을 수 있다
```

이 문제를 해결하는 방법:

- 파티션 키를 동일하게 설정해 같은 Partition으로 보낸다.
- Consumer에서 이벤트 순서 검증 로직을 추가한다.
- 이벤트에 시퀀스 번호나 타임스탬프를 포함해 순서를 확인한다.

### Dead Letter Queue (DLQ)

Consumer가 이벤트를 처리하지 못하고 계속 실패하면, 이 이벤트 때문에 Partition 처리 전체가 막힐 수 있다. DLQ는 일정 횟수 이상 실패한 이벤트를 별도 Topic으로 보내 정상 처리 흐름이 막히지 않도록 한다.

```text
정상 처리 흐름:
transfer-events Topic → Consumer 처리 성공

실패 시:
transfer-events Topic → Consumer 처리 실패 (3회 재시도)
                                            → transfer-events-dlt Topic (DLQ)
                                               → 운영자 확인 및 수동 재처리
```

### 관측성과 분산 추적

EDA에서는 하나의 요청이 여러 서비스를 비동기로 거치므로 추적이 어렵다. Correlation ID(또는 Trace ID)를 이벤트에 포함시켜 전체 흐름을 추적할 수 있어야 한다.

```json
{
  "eventId": "evt-123",
  "correlationId": "req-abc",   // 최초 HTTP 요청부터 이어지는 ID
  "eventType": "TransferCompleted",
  ...
}
```

Kafka Consumer가 이벤트를 처리할 때 이 `correlationId`를 로그에 기록하면, 하나의 요청이 어떤 서비스들을 거쳐 처리됐는지 추적할 수 있다.

### EDA의 trade-off

| 장점 | 단점 |
|------|------|
| 서비스 결합도 감소 | 흐름 추적 어려움 |
| 장애 격리 | 디버깅 복잡도 증가 |
| 스케일아웃 용이 | 이벤트 순서 보장 어려움 |
| 새 Consumer 추가 용이 | 최종 정합성만 보장 |
| 이벤트 재처리 가능 | 인프라 운영 복잡도 |

EDA는 모든 상황에 적합하지 않다. 실시간 응답이 필요하거나, 처리 결과를 즉시 확인해야 하거나, 강한 일관성이 필요한 경우에는 동기 방식이 더 적합할 수 있다. 결제 시스템에서는 핵심 처리(잔액 차감)는 동기로, 후속 처리(원장 기록, 정산)는 이벤트 기반으로 분리하는 혼합 방식이 일반적이다.

## PayFlow 연결

PayFlow에서 송금 완료 후 원장 기록과 정산 처리는 이벤트 기반으로 분리할 수 있다.

```text
transfer-service -> TransferCompleted -> Kafka -> ledger-service
                                            -> settlement-service
```

이렇게 하면 `transfer-service`가 원장과 정산 서비스의 내부 구현을 몰라도 된다.

## 실무 포인트

- 이벤트는 유실되면 안 된다.
- Consumer는 멱등해야 한다.
- 이벤트 순서를 보장해야 하는 단위를 정한다.
- 실패 메시지 처리를 설계한다.
- 이벤트 추적을 위한 로그와 correlation id가 필요하다.

## 체크 질문

- 이벤트 기반 구조의 장점과 단점은 무엇인가
- PayFlow에서 송금과 원장 기록을 이벤트로 분리하는 이유는 무엇인가
- 이벤트 기반 시스템에서 관측성이 중요한 이유는 무엇인가

## 실무 설계 참고

### 대표 장애 시나리오

ledger-service 장애가 송금 API 응답 장애로 직접 전파된다.

### 잘못된 구현 예시

~~~text
모든 후속 처리를 동기 호출로 연결한다.
~~~

### 좋은 구현 예시

~~~text
송금 완료 후 후속 처리는 이벤트로 분리하고 Outbox와 Consumer 멱등성을 둔다.
~~~

### 대안과 선택 이유

분산 트랜잭션이나 모든 후속 처리를 동기 호출로 묶는 방식도 있지만, 서비스 하나의 장애가 전체 송금 실패로 번지기 쉽다. PayFlow는 로컬 트랜잭션, Saga, 도메인 이벤트, Outbox, 멱등성을 조합해 부분 실패를 상태로 남기고 복구하는 방식을 선택하는 것이 적합하다.

### PayFlow에서 확인할 위치

transfer-service publisher, Kafka, ledger-service consumer

### 면접에서 설명하기

이벤트 기반 구조는 결합도를 낮추지만 중복과 지연을 설계해야 한다.

### 관련 문서

04, 36, 53, 60

## 질문의 질문으로 더 깊게 보기

### 이 문서에서 잡아야 할 CS 감각

이 구간의 핵심은 여러 서비스의 작업을 하나의 로컬 트랜잭션처럼 다룰 수 없다는 사실이다. 그래서 Saga, Domain Event, Outbox, 멱등성이 등장한다. 이들은 모두 "부분 실패를 어떻게 추적하고 복구할 것인가"에 대한 답이다.

### 질문의 질문

아래 질문들은 답을 외우기 위한 질문이 아니다. 하나씩 답하면서 "왜 그런 설계가 필요한지"를 설명하는 연습을 하기 위한 질문이다.

- Event Driven Architecture 개념이 없다면 PayFlow에서 가장 먼저 어떤 장애가 생기는가?
- 이 개념은 정확성, 성능, 보안, 운영성 중 무엇을 가장 크게 개선하는가?
- 반대로 이 개념을 잘못 적용하면 어떤 복잡도가 추가되는가?
- 이 개념을 적용하면 어떤 문제가 해결되지만, 어떤 비용이나 복잡도가 새로 생기는가?
- 장애가 발생했을 때 이 개념은 문제를 예방하는가, 감지하는가, 복구하는가?
- PayFlow 코드에서 이 개념을 확인하려면 어떤 서비스, 테이블, 로그, 테스트를 봐야 하는가?

### 답안 예시

<details>
<summary>답안 예시 펼치기</summary>

#### 핵심 답변

Event Driven Architecture 개념은 PayFlow에서 다음 이유로 중요하다.

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
Event Driven Architecture 개념은 PayFlow에서 송금 처리와 원장/정산 후속 처리를 비동기로 분리하면서 신뢰성을 유지하기 위해 필요하다.
이 개념이 없으면 이벤트 유실이나 중복 소비가 생기면 송금 완료와 원장/정산 상태가 어긋날 수 있다.
그래서 코드에서는 Kafka, Outbox, Consumer 멱등성, DLQ, trace id를 함께 설계하고,
운영에서는 event lag, DLQ, 중복 차단, trace별 처리 경로를 확인해야 한다.
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
Event Driven Architecture 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.

