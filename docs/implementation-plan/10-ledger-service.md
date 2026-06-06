# 10. Ledger Service

`ledger-service`는 결제 회사 지원용 포트폴리오에서 매우 중요한 서비스다.

목표는 "잔액이 바뀐 근거를 불변 원장으로 남긴다"는 관점을 보여주는 것이다.

## 목표

구현할 기능:

```text
transfer.completed 이벤트 소비
차변/대변 원장 기록
sourceEventId 기반 consumer 멱등성 처리
원장 조회
원장 합계 검증
```

## Kafka Consumer

구독 topic:

```text
transfer.completed
transfer.failed
```

초기에는 `transfer.completed`만 구현해도 된다.

## 원장 기록 규칙

송금 10,000원:

```text
sender wallet   DEBIT   10,000
receiver wallet CREDIT  10,000
```

주의:

```text
DEBIT/CREDIT 명칭은 회계 관점에 따라 해석이 달라질 수 있다.
포트폴리오에서는 "송신자 차감 라인"과 "수신자 증가 라인"을 명확히 설명한다.
```

대안 명칭:

```text
OUT
IN
```

초기 구현에서는 이해하기 쉬운 `DEBIT`, `CREDIT`을 사용하되 README에 설명한다.

## 엔티티

### LedgerEntry

```text
id
transferId
sourceEventId
entryType
totalAmount
createdAt
```

### LedgerLine

```text
id
ledgerEntryId
walletId
direction
amount
createdAt
```

### ProcessedEvent

```text
id
sourceEventId
consumerName
processedAt
```

## 처리 흐름

```text
1. Kafka event 수신
2. processed_events에서 sourceEventId 조회
3. 이미 있으면 skip
4. LedgerEntry 생성
5. LedgerLine 2개 생성
6. ProcessedEvent 저장
7. commit
```

모든 작업은 하나의 DB 트랜잭션에서 처리한다.
`sourceEventId`는 transfer-service OutboxEvent의 `eventId`를 그대로 저장한 값이며, `processed_events.source_event_id`에 unique 제약을 둔다.

## 원장 조회 API

### 송금별 원장 조회

```http
GET /ledgers/transfers/{transferId}
```

응답:

```json
{
  "transferId": 1001,
  "sourceEventId": "uuid",
  "totalAmount": 10000,
  "lines": [
    {
      "walletId": 1,
      "direction": "DEBIT",
      "amount": 10000
    },
    {
      "walletId": 2,
      "direction": "CREDIT",
      "amount": 10000
    }
  ]
}
```

## 검증 규칙

원장 한 건은 반드시 line 2개를 가진다.

```text
sender line amount == receiver line amount
totalAmount == line amount
sourceEventId unique
transferId unique 또는 중복 방지
```

## 구현 순서

1. 엔티티 작성
2. Repository 작성
3. TransferCompletedEvent DTO 작성
4. ProcessedEventRepository 작성
5. LedgerApplicationService 작성
6. Kafka Consumer 작성
7. 조회 API 작성
8. 테스트 작성

## 테스트

필수 테스트:

```text
transfer.completed 이벤트 수신 시 원장 기록
line 2개 생성 확인
sourceEventId 중복 소비 시 skip
원장 조회 성공
잘못된 amount 이벤트 실패
```
