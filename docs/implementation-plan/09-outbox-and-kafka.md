# 09. Outbox And Kafka

이 문서는 Kafka 이벤트 발행 신뢰성을 위한 Transactional Outbox 구현 계획이다.

## 문제

아래 상황을 방지해야 한다.

```text
DB에는 송금 성공으로 저장됨
Kafka 이벤트 발행은 실패함
ledger-service는 원장 기록을 못 함
settlement-service도 정산 대상 누락
```

## 해결

`transfer-service`의 DB 트랜잭션 안에서 Kafka를 직접 발행하지 않는다.

대신 같은 DB 트랜잭션 안에서 `outbox_events`에 이벤트를 저장한다.

이후 별도 publisher가 `READY` 이벤트를 Kafka로 발행한다.

## 이벤트 타입

초기 이벤트:

```text
transfer.completed
transfer.failed
```

추후 이벤트:

```text
ledger.recorded
settlement.completed
```

## OutboxEvent payload

TransferCompleted payload:

```json
{
  "eventId": "uuid",
  "transferId": 1001,
  "senderWalletId": 1,
  "receiverWalletId": 2,
  "amount": 10000,
  "completedAt": "2026-05-30T12:00:00"
}
```

TransferFailed payload:

```json
{
  "eventId": "uuid",
  "transferId": 1001,
  "senderWalletId": 1,
  "receiverWalletId": 2,
  "amount": 10000,
  "status": "FAILED",
  "failureReason": "INSUFFICIENT_BALANCE",
  "failedAt": "2026-05-30T12:00:00"
}
```

`COMPENSATION_REQUIRED`는 단순 실패보다 더 위험한 상태이므로 `transfer.failed` payload에 그대로 담아 후속 서비스와 운영자가 구분할 수 있게 한다.

```json
{
  "eventId": "uuid",
  "transferId": 1002,
  "senderWalletId": 1,
  "receiverWalletId": 2,
  "amount": 10000,
  "status": "COMPENSATION_REQUIRED",
  "failureReason": "RECEIVER_DEPOSIT_FAILED_AFTER_SENDER_WITHDRAW",
  "failedAt": "2026-05-30T12:01:00"
}
```

## Outbox 상태

```text
READY
PUBLISHING
PUBLISHED
FAILED
```

정책:

```text
READY: 발행 대상
PUBLISHING: publisher가 발행 권한을 선점한 상태
PUBLISHED: 발행 완료
FAILED: 재시도 한도 초과 또는 일시 실패
```

초기 구현에서는 실패해도 `FAILED`로 바로 두지 않고 retryCount만 올린다.
retryCount가 maxRetry를 넘으면 `FAILED`로 바꾸고 lastError를 남긴다.

## Publisher 구현

위치:

```text
transfer-service/infrastructure/kafka
```

클래스:

```text
OutboxEventPublisher
OutboxEventScheduler
KafkaTransferEventProducer
```

흐름:

```text
1. READY 이벤트 N건을 PUBLISHING으로 선점
2. Kafka topic으로 발행
3. 성공 시 PUBLISHED 변경
4. 실패 시 retryCount 증가, lastError 저장 후 READY로 되돌림
5. retryCount가 maxRetry 이상이면 FAILED 변경
```

초기에는 `@Scheduled` 사용:

```text
fixedDelay: 1000ms
batch size: 50
```

## Publisher 중복 처리

Outbox publisher가 여러 개 실행될 수 있으므로 같은 이벤트를 동시에 발행하지 않도록 선점 규칙을 둔다.

권장 구현:

```text
READY 이벤트를 조회할 때 DB row lock을 사용한다.
가능하면 SELECT ... FOR UPDATE SKIP LOCKED 또는 JPA pessimistic lock을 사용한다.
선점에 성공한 row만 READY -> PUBLISHING으로 변경한다.
PUBLISHING 상태가 일정 시간 이상 유지되면 READY로 복구한다.
```

복구 기준:

```text
updatedAt 기준 5분 이상 PUBLISHING이면 publisher 장애로 보고 READY로 되돌린다.
eventId는 항상 UNIQUE로 유지한다.
consumer 멱등성이 최종 중복 방어선이다.
```

## 중복 발행 가능성

Outbox는 at-least-once 발행이다.

따라서 consumer는 반드시 멱등해야 한다.

consumer 쪽 방어:

```text
processed_events.source_event_id UNIQUE
이미 처리한 sourceEventId면 skip
```

## Kafka topic

```text
transfer.completed
transfer.failed
ledger.recorded
```

## Retry와 DLQ

Outbox publisher의 retry와 Kafka consumer의 retry는 분리해서 본다.

```text
Outbox publisher retry:
- Kafka 발행 자체가 실패한 경우
- outbox_events.retryCount 증가
- 한도 초과 시 FAILED 상태로 남김

Kafka consumer retry:
- Kafka 발행은 성공했지만 consumer 처리 중 실패한 경우
- consumer retry 후에도 실패하면 DLQ topic으로 보냄
```

초기 DLQ topic:

```text
transfer.completed.dlq
transfer.failed.dlq
ledger.recorded.dlq
```

DLQ payload에는 원본 이벤트와 실패 원인을 함께 남긴다.

```text
eventId
originalTopic
consumerGroup
failureReason
attempts
failedAt
payload
```

DLQ는 자동 성공 처리가 아니라 운영자가 원인을 확인하고 재처리할 수 있게 하는 마지막 안전망이다.

개발 환경:

```text
partition: 1
replication factor: 1
```

## 테스트

필수 테스트:

```text
송금 성공 시 OutboxEvent READY 저장
Publisher가 Kafka 발행 후 PUBLISHED 변경
Kafka 발행 실패 시 retryCount 증가
retry 한도 초과 시 FAILED 변경
여러 publisher가 동시에 실행되어도 같은 row를 중복 선점하지 않음
stale PUBLISHING 이벤트 READY 복구
같은 eventId 중복 발행 시 consumer 한 번만 처리
READY 이벤트 여러 건 batch 처리
```
