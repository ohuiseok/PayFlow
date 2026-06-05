# 17. Payment Core Hardening Roadmap

이 문서는 PayFlow를 결제 시스템 포트폴리오답게 보이게 만드는 핵심 5요소의 구현 로드맵이다.

현재 구현된 `user-service`, `wallet-service`, `api-gateway` 기반은 유지한다. 이후 작업은 기존 구조를 갈아엎는 것이 아니라, `transfer-service`, `banking-service`, Kafka/Outbox, 장애 복구 문서에 자연스럽게 붙여 결제 흐름의 신뢰성을 높이는 방향으로 진행한다.

## 핵심 5요소

```text
1. 상태 머신
2. Idempotency
3. Outbox Pattern
4. Retry/DLQ
5. Mock PG
```

이 5가지는 별도 기능처럼 보이지만 실제로는 하나의 결제 흐름 안에서 연결된다.

```text
Mock PG 응답
-> 상태 머신 전이
-> Idempotency로 중복 요청 방어
-> Outbox로 후속 이벤트 저장
-> Retry/DLQ로 실패 이벤트와 외부 호출 복구
```

## 현재 기반

현재 코드 기준으로 이미 활용할 수 있는 기반은 다음과 같다.

```text
api-gateway:
- JWT 인증
- X-User-Id 전달
- 외부 X-User-* 헤더 제거

user-service:
- 회원가입
- 로그인
- 사용자 조회

wallet-service:
- 지갑 생성
- 잔액 조회
- 충전/차감
- 지갑 거래 이력
- wallet transaction reference 기반 중복 반영 방어
- DB row lock 기반 잔액 변경

docker/infra:
- MySQL, Redis, Kafka 실행 기반
- 서비스별 Docker Compose 구성
```

위 기반은 유지한다. 앞으로 구현할 핵심은 `transfer-service`와 `banking-service`가 상태를 소유하고, 이벤트와 장애 복구가 그 상태를 기준으로 움직이게 만드는 것이다.

## 1. 상태 머신

### transfer-service

송금은 단순 성공/실패 값이 아니라 상태 전이로 관리한다.

```text
REQUESTED
-> PROCESSING
-> COMPLETED

PROCESSING
-> FAILED
-> COMPENSATION_REQUIRED

COMPENSATION_REQUIRED
-> ROLLED_BACK
-> ROLLBACK_FAILED
```

규칙:

```text
COMPLETED 이후에는 FAILED로 되돌리지 않는다.
실패는 failureReason과 함께 상태로 남긴다.
sender 차감 성공 후 receiver 증가 실패는 FAILED가 아니라 COMPENSATION_REQUIRED로 남긴다.
PROCESSING 상태가 오래 남으면 stale 복구 대상으로 본다.
```

### banking-service

Mock PG 또는 오픈뱅킹 테스트베드 응답도 상태로 관리한다.

```text
REQUESTED
-> PG_PROCESSING
-> PG_SUCCEEDED
-> WALLET_REFLECTING
-> COMPLETED

PG_PROCESSING
-> FAILED
-> UNKNOWN

WALLET_REFLECTING
-> COMPENSATION_REQUIRED
```

`banking-service`는 외부 PG/은행망 요청 상태의 진실을 가진다. `wallet-service`는 은행망 상태를 알지 않고, 내부 API 요청을 받은 뒤 reference 기반으로 잔액을 반영한다.

## 2. Idempotency

멱등성은 API 요청 단위와 잔액 반영 단위에 모두 둔다.

### transfer-service API

```text
Idempotency-Key 필수
request body hash 저장
같은 key + 같은 body -> 기존 응답 반환
같은 key + 다른 body -> 409 Conflict
PROCESSING 상태 재요청 -> 202 Accepted 또는 409 Conflict 중 정책 선택
```

저장 대상:

```text
idempotency_keys
- idempotency_key
- request_hash
- resource_type
- resource_id
- status
- response_body
- created_at
- updated_at
```

### banking-service API

충전/출금도 같은 규칙을 사용한다.

추가 방어:

```text
bank_tran_id UNIQUE
wallet referenceType/referenceId UNIQUE
```

즉 같은 충전 요청이 여러 번 들어와도 은행망 거래와 지갑 반영이 각각 한 번만 일어나야 한다.

## 3. Outbox Pattern

송금 성공과 실패 이벤트는 DB 트랜잭션 안에서 Kafka로 직접 발행하지 않는다.

```text
Transfer DB Transaction
  - transfer 상태 저장
  - idempotency 결과 저장
  - outbox_event READY 저장

Outbox Publisher
  - READY 이벤트 선점
  - Kafka 발행
  - PUBLISHED 또는 FAILED 상태 반영
```

초기 이벤트:

```text
transfer.completed
transfer.failed
```

원칙:

```text
Outbox는 at-least-once 발행을 전제로 한다.
consumer는 eventId 기준으로 멱등 처리한다.
Kafka 발행 실패는 송금 성공을 되돌리지 않는다.
발행 실패는 outbox 상태와 retryCount로 남긴다.
```

## 4. Retry/DLQ

Retry는 모든 실패에 무조건 적용하지 않는다. 결제 변경 요청에 retry를 걸려면 중복 방어가 먼저 있어야 한다.

### 동기 호출 Retry

대상:

```text
transfer-service -> wallet-service
banking-service -> wallet-service
banking-service -> Mock PG/OpenBankingClient
```

규칙:

```text
timeout, connection refused 같은 네트워크성 오류만 제한적으로 retry한다.
잔액 부족, 소유권 실패, 잘못된 요청은 retry하지 않는다.
retry 가능한 변경 요청은 반드시 Idempotency-Key 또는 referenceId를 가진다.
CircuitBreaker와 TimeLimiter를 함께 둔다.
```

### Kafka Retry/DLQ

consumer 처리 실패는 재시도 후 DLQ로 보낸다.

초기 DLQ topic:

```text
transfer.completed.dlq
transfer.failed.dlq
ledger.recorded.dlq
```

DLQ payload에는 재처리 근거를 남긴다.

```text
eventId
originalTopic
consumerGroup
failureReason
attempts
failedAt
payload
```

DLQ는 실패를 숨기는 저장소가 아니라 운영자가 재처리하거나 원인을 분석할 수 있는 근거다.

## 5. Mock PG

`banking-service`에 실제 PG/은행망 대신 사용할 mock adapter를 먼저 구현한다.

인터페이스:

```java
public interface PaymentGatewayClient {
    PgPaymentResponse requestPayment(PgPaymentRequest request);
    PgTransferResultResponse getTransferResult(PgTransferResultRequest request);
}
```

구현:

```text
MockPaymentGatewayClient
KftcOpenBankingClient 또는 TestbedPaymentGatewayClient
```

Mock PG가 제공해야 하는 응답:

```text
성공
명시 실패
timeout
처리 중
거래 ID 중복
응답 불명
```

Mock PG는 단순 fake가 아니라 장애 시나리오를 재현하는 테스트 도구다. 상태 머신, Idempotency, Retry, 결과조회 워커를 검증하는 기준으로 사용한다.

## 구현 순서

### Phase 1. transfer-service 닫힌 루프

```text
1. Transfer/TransferStatus 구현
2. IdempotencyKey 구현
3. WalletClient 구현
4. 송금 요청/조회 API 구현
5. 상태 전이와 실패 상태 저장
6. 동일 Idempotency-Key 재요청 테스트
```

완료 기준:

```text
같은 송금 요청을 여러 번 보내도 sender 지갑은 한 번만 차감된다.
```

### Phase 2. Outbox와 ledger 연동

```text
1. OutboxEvent 구현
2. 송금 완료/실패 시 OutboxEvent READY 저장
3. Outbox publisher 구현
4. Kafka 발행 성공 시 PUBLISHED 처리
5. ledger-service consumer 구현
6. processed_events 기반 consumer 멱등성 구현
```

완료 기준:

```text
송금 성공 이벤트가 Kafka를 통해 원장에 한 번만 기록된다.
```

### Phase 3. Retry/DLQ

```text
1. wallet-service 호출에 CircuitBreaker/Retry/TimeLimiter 적용
2. Outbox publisher retryCount와 FAILED 상태 구현
3. Kafka consumer retry 설정
4. DLQ topic과 DLQ payload 정의
5. DLQ 재처리 또는 운영 확인 절차 문서화
```

완료 기준:

```text
일시 장애는 재시도로 회복하고, 반복 실패는 DLQ와 실패 상태로 추적된다.
```

### Phase 4. Mock PG 기반 banking-service

```text
1. PaymentGatewayClient 또는 OpenBankingClient 인터페이스 구현
2. MockPaymentGatewayClient 구현
3. BankingTransfer 상태 머신 구현
4. 충전 API 구현
5. PG 성공 확정 후 wallet-service deposit 호출
6. UNKNOWN/PG_PROCESSING 결과조회 워커 구현
```

완료 기준:

```text
Mock PG의 성공/실패/timeout/처리중 응답에 따라 banking-service 상태가 정확히 전이된다.
```

### Phase 5. 포트폴리오 정리

```text
1. README에 현재 구현 범위와 후속 범위를 분리해서 정리
2. 장애 시나리오별 테스트 결과 정리
3. 핵심 5요소가 어떤 장애를 막는지 설명 추가
4. 면접 답변용 설계 요약 작성
```

## 면접 설명 기준

최종적으로 아래 문장을 코드와 테스트로 설명할 수 있어야 한다.

```text
PayFlow는 결제 요청을 상태 머신으로 추적하고, Idempotency-Key로 중복 요청을 방어합니다.
DB 상태 변경과 Kafka 발행은 Outbox Pattern으로 분리해 이벤트 유실을 줄였고,
일시 장애는 제한적 Retry로 복구하며 반복 실패는 DLQ와 실패 상태로 남깁니다.
외부 PG/은행망은 Mock PG로 먼저 모델링해 성공, 실패, timeout, 응답 불명 시나리오를 테스트할 수 있게 했습니다.
```
