# 05. Transfer Service

`transfer-service`는 송금 요청의 상태를 관리하고, wallet-service를 호출해 돈의 이동을 오케스트레이션한다.

중요: `transfer-service`는 잔액을 직접 변경하지 않는다.
외부 은행 계좌 출금/입금은 담당하지 않는다. 은행망 연동은 `banking-service`가 맡고, `transfer-service`는 이미 충전된 PayFlow 지갑 간 송금만 처리한다.

## 목표

구현할 기능:

```text
송금 요청 생성
송금 상태 관리
Idempotency-Key 검증
wallet-service 호출
실패 상태 기록
Outbox 이벤트 저장
송금 조회
```

## API

### 송금 요청

```http
POST /transfers
Idempotency-Key: 20260530-user1-transfer-001
Authorization: Bearer {token}
Content-Type: application/json

{
  "senderWalletId": 1,
  "receiverWalletId": 2,
  "amount": 10000
}
```

응답:

```json
{
  "transferId": 1001,
  "status": "COMPLETED",
  "amount": 10000
}
```

### 송금 조회

```http
GET /transfers/{transferId}
```

응답:

```json
{
  "transferId": 1001,
  "senderWalletId": 1,
  "receiverWalletId": 2,
  "amount": 10000,
  "status": "COMPLETED"
}
```

## 엔티티

### Transfer

```text
id
senderWalletId
receiverWalletId
amount
status
failureReason
idempotencyKey
createdAt
updatedAt
```

TransferStatus:

```text
REQUESTED
PROCESSING
COMPLETED
FAILED
COMPENSATION_REQUIRED
ROLLED_BACK
ROLLBACK_FAILED
```

### IdempotencyKey

```text
id
idempotencyKey
requestHash
resourceType
resourceId
status
responseBody
createdAt
updatedAt
```

IdempotencyStatus:

```text
PROCESSING
COMPLETED
FAILED
```

### OutboxEvent

```text
id
eventId
aggregateType
aggregateId
eventType
payload
status
retryCount
lastError
createdAt
publishedAt
```

OutboxStatus:

```text
READY
PUBLISHED
FAILED
```

## 소유권 규칙

송금 요청은 인증 사용자 기준으로 검증한다.

```text
senderWalletId는 X-User-Id 사용자의 지갑이어야 한다.
receiverWalletId는 존재하고 ACTIVE 상태여야 한다.
senderWalletId와 receiverWalletId가 같으면 실패시킨다.
transfer-service는 wallet-service의 소유권 확인 API 또는 내부 조회 API를 통해 지갑 정보를 확인한다.
Gateway가 전달한 X-User-Id가 없으면 요청을 거부한다.
```

## 상태 전이 규칙

허용 전이:

```text
REQUESTED -> PROCESSING
PROCESSING -> COMPLETED
PROCESSING -> FAILED
PROCESSING -> COMPENSATION_REQUIRED
COMPENSATION_REQUIRED -> ROLLED_BACK
COMPENSATION_REQUIRED -> ROLLBACK_FAILED
FAILED -> PROCESSING, 단 명시적인 재처리 정책이 있는 경우만 허용
```

금지 전이:

```text
COMPLETED -> FAILED
COMPLETED -> PROCESSING
ROLLED_BACK -> COMPLETED
ROLLBACK_FAILED -> COMPLETED
```

상태 변경 시 `failureReason` 또는 처리 근거를 함께 남긴다.

## 송금 처리 흐름

기본 흐름:

```text
1. Idempotency-Key header 확인
2. 요청 body hash 계산
3. idempotency_keys에서 key 조회
4. 없으면 PROCESSING으로 저장
5. Transfer REQUESTED 저장
6. Transfer PROCESSING 변경
7. sender wallet withdraw 호출
8. receiver wallet deposit 호출
9. Transfer COMPLETED 변경
10. OutboxEvent READY 저장
11. IdempotencyKey COMPLETED 변경
12. 응답 반환
```

중복 요청 흐름:

```text
1. 같은 Idempotency-Key 조회
2. requestHash가 다르면 409 Conflict
3. status가 COMPLETED면 기존 responseBody 반환
4. status가 PROCESSING이면 409 또는 202 반환
5. status가 FAILED면 정책에 따라 실패 반환 또는 재시도 허용
```

## 실패 처리

### sender 차감 실패

```text
Transfer FAILED
failureReason 저장
OutboxEvent 저장하지 않음
```

### sender 차감 성공, receiver 증가 실패

초기 구현에서도 `FAILED`로만 끝내지 않는다.

```text
Transfer COMPENSATION_REQUIRED
failureReason 저장
운영자 또는 복구 배치가 sender wallet 보상 입금을 수행할 수 있도록 근거를 남김
```

보상 처리 고도화:

```text
sender wallet 보상 입금 성공 -> ROLLED_BACK
sender wallet 보상 입금 실패 -> ROLLBACK_FAILED
```

README와 테스트에 이 케이스를 반드시 남긴다.

## PROCESSING 복구 규칙

transfer-service 재시작이나 timeout으로 `PROCESSING` 상태가 남을 수 있다.

초기 정책:

```text
updatedAt 기준 5분 이상 PROCESSING이면 조사 대상 stale transfer로 본다.
자동 완료 처리는 하지 않는다.
관리 API 또는 복구 배치에서 wallet transaction referenceId와 Outbox 상태를 확인한다.
차감/입금이 모두 확인되면 COMPLETED + OutboxEvent READY를 저장한다.
차감만 확인되면 COMPENSATION_REQUIRED로 변경한다.
차감이 확인되지 않으면 FAILED로 변경한다.
```

## OpenFeign Client

transfer-service에 wallet-service client를 둔다.

```java
@FeignClient(name = "wallet-service", url = "${clients.wallet-service.url}")
```

환경변수:

```yaml
clients:
  wallet-service:
    url: ${WALLET_SERVICE_URL:http://localhost:8082}
```

Docker 환경:

```text
WALLET_SERVICE_URL=http://wallet-service:8082
```

## Resilience4j

wallet-service 호출에 적용한다.

```text
CircuitBreaker
Retry
TimeLimiter
Fallback
```

주의:

```text
잔액 차감 같은 변경 요청은 무분별한 retry가 위험하다.
retry는 네트워크 오류와 timeout에만 제한적으로 적용한다.
idempotency/referenceId로 wallet-service 쪽 중복 방어도 필요하다.
```

## 구현 순서

1. Transfer 엔티티 작성
2. IdempotencyKey 엔티티 작성
3. OutboxEvent 엔티티 작성
4. Repository 작성
5. request hash 유틸 작성
6. IdempotencyService 작성
7. WalletClient 작성
8. TransferApplicationService 작성
9. Outbox 저장 로직 작성
10. Controller 작성
11. 실패 케이스 테스트

## 테스트

필수 테스트:

```text
송금 성공
Idempotency-Key 없음 실패
동일 key 동일 요청 재호출 시 같은 응답
동일 key 다른 요청 재호출 시 409
senderWalletId가 인증 사용자 소유가 아니면 실패
sender와 receiver가 같으면 실패
sender 잔액 부족 시 FAILED
wallet-service 장애 시 FAILED
sender 차감 성공 후 receiver 증가 실패 시 COMPENSATION_REQUIRED
PROCESSING stale transfer 복구 정책 검증
OutboxEvent 저장 확인
송금 상태 전이 검증
```
