# 01. Common Rules

이 문서는 모든 서비스에 공통으로 적용할 구현 규칙이다.

## 기술 기준

```text
Java 21
Spring Boot 4.0.6
Spring Cloud 2025.1.1
MySQL 8.x
Redis 7.x
Kafka
Gradle
Docker Compose
```

## 공통 패키지 구조

각 서비스는 아래 구조를 기본으로 한다.

```text
com.payflow.{service}
  api
  application
  domain
  domain.model
  domain.repository
  infrastructure
  infrastructure.persistence
  infrastructure.kafka
  infrastructure.client
  support
  support.error
```

서비스별 예시:

```text
com.payflow.wallet
com.payflow.transfer
com.payflow.ledger
```

## 레이어 규칙

### api

역할:

```text
Controller
Request DTO
Response DTO
HTTP status mapping
```

금지:

```text
비즈니스 로직
JPA Entity 직접 반환
다른 서비스 호출 직접 수행
```

### application

역할:

```text
UseCase
Service orchestration
Transaction boundary
외부 서비스 호출 조합
```

주의:

```text
도메인 규칙은 domain에 둔다.
application은 흐름을 조립한다.
```

### domain

역할:

```text
Entity
Value Object
Enum
도메인 규칙
```

원칙:

```text
금액 검증
상태 전이 검증
잔액 음수 방지
원장 차변/대변 검증
```

### infrastructure

역할:

```text
JPA repository 구현
Kafka producer/consumer
Redis lock
OpenFeign client
외부 시스템 어댑터
```

## DTO 규칙

Java `record`를 기본으로 사용한다.

예시:

```java
public record CreateWalletRequest(Long userId) {
}
```

단, JPA Entity는 `class`로 작성한다.

## ID 규칙

초기에는 구현 속도를 위해 Long ID를 사용한다.

```text
userId: Long
walletId: Long
transferId: Long
ledgerEntryId: Long
```

추후 외부 노출용 ID가 필요하면 별도 publicId를 추가한다.

## 금액 규칙

금액은 반드시 `BigDecimal`을 사용한다.

규칙:

```text
null 금지
0 이하 금액 금지
scale은 0 또는 2 중 하나로 통일
초기에는 원화 기준 scale 0 사용
```

권장 컬럼:

```text
DECIMAL(19, 0)
```

입력 정책:

```text
JSON 요청에서는 숫자로 받되 DTO validation과 도메인 검증을 모두 적용한다.
소수 입력은 원화 기준 초기 구현에서 거부한다.
최소 송금 금액은 1원이다.
최대 송금 금액은 초기 구현에서 10,000,000원으로 제한한다.
1일 송금 한도는 구현하지 않고 README에 제외 범위로 명시한다.
```

## 시간 규칙

엔티티 시간 필드는 아래를 기본으로 한다.

```java
private LocalDateTime createdAt;
private LocalDateTime updatedAt;
```

시간대는 서버와 DB 모두 `Asia/Seoul` 기준으로 맞춘다.

## 예외 규칙

서비스별 공통 예외 구조를 둔다.

```text
BusinessException
ErrorCode
GlobalExceptionHandler
ErrorResponse
```

ErrorResponse 예시:

```json
{
  "code": "INSUFFICIENT_BALANCE",
  "message": "잔액이 부족합니다.",
  "traceId": "optional"
}
```

주요 ErrorCode:

```text
INVALID_REQUEST
UNAUTHORIZED
INVALID_TOKEN
TOKEN_EXPIRED
FORBIDDEN
INTERNAL_HEADER_FORBIDDEN
RESOURCE_NOT_FOUND
RESOURCE_OWNER_MISMATCH
USER_ALREADY_EXISTS
INVALID_CREDENTIALS
WALLET_NOT_FOUND
WALLET_LOCKED
INSUFFICIENT_BALANCE
DUPLICATE_WALLET
DUPLICATE_WALLET_REFERENCE
IDEMPOTENCY_KEY_REQUIRED
IDEMPOTENCY_REQUEST_MISMATCH
TRANSFER_NOT_FOUND
INVALID_TRANSFER_STATUS
WALLET_LOCK_CONFLICT
OUTBOX_PUBLISH_FAILED
LEDGER_DUPLICATE_EVENT
SETTLEMENT_ALREADY_COMPLETED
INTERNAL_SERVER_ERROR
```

## 로그 규칙

반드시 남길 로그:

```text
transferId
walletId
idempotencyKey
eventId
outboxId
status
failureReason
```

요청 추적:

```text
Gateway에서 traceId 또는 correlationId를 생성한다.
Idempotency-Key가 있으면 traceId와 함께 모든 서비스 로그에 남긴다.
서비스 간 호출과 Kafka payload에는 eventId, transferId를 포함한다.
```

금지:

```text
JWT 원문 로그
비밀번호 로그
개인정보 과다 로그
```

## 트랜잭션 규칙

DB 변경이 있는 application service 메서드는 `@Transactional`을 붙인다.

조회 전용은:

```java
@Transactional(readOnly = true)
```

서비스 간 분산 트랜잭션은 사용하지 않는다.

## 테스트 규칙

최소 테스트:

```text
도메인 단위 테스트
application service 테스트
controller smoke test
repository 테스트
동시성 테스트
멱등성 테스트
Outbox 재발행 테스트
```

Testcontainers는 이후 통합 테스트에서 사용한다.
