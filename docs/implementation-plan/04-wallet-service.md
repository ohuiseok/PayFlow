# 04. Wallet Service

`wallet-service`는 PayFlow의 핵심 서비스다.

잔액의 진실은 반드시 이 서비스에만 있어야 한다.

## 목표

구현할 기능:

```text
지갑 생성
잔액 조회
잔액 충전
잔액 차감
잔액 증가
잔액 변경 이력 저장
```

## API

### 지갑 생성

```http
POST /wallets
X-User-Id: 1
Content-Type: application/json

{
  "userId": 1
}
```

응답:

```json
{
  "walletId": 1,
  "userId": 1,
  "balance": 0,
  "status": "ACTIVE"
}
```

### 잔액 조회

```http
GET /wallets/{walletId}
```

응답:

```json
{
  "walletId": 1,
  "userId": 1,
  "balance": 10000,
  "status": "ACTIVE"
}
```

### 충전

초기에는 개발/시연용 API로 둔다.

```http
POST /wallets/{walletId}/deposit
X-User-Id: 1
Content-Type: application/json

{
  "amount": 10000,
  "referenceType": "MANUAL_CHARGE",
  "referenceId": 1
}
```

오픈뱅킹 충전이 도입되면 외부 공개 충전 API는 개발/시연용으로만 유지한다.
실제 포트폴리오 충전 시나리오는 `banking-service`가 오픈뱅킹 출금이체 성공을 확인한 뒤 내부 API로 호출한다.

```http
POST /wallets/{walletId}/deposit
X-Internal-Request: true
Content-Type: application/json

{
  "amount": 10000,
  "referenceType": "OPEN_BANKING_CHARGE",
  "referenceId": "M20260601123456789"
}
```

### 송금 차감

내부 API로 시작한다. 외부 사용자가 직접 호출할 수 없게 Gateway에서 차단한다.

```http
POST /wallets/{walletId}/withdraw
X-Internal-Request: true
Content-Type: application/json

{
  "amount": 10000,
  "referenceType": "TRANSFER",
  "referenceId": 1001
}
```

### 송금 증가

```http
POST /wallets/{walletId}/deposit
X-Internal-Request: true
Content-Type: application/json

{
  "amount": 10000,
  "referenceType": "TRANSFER",
  "referenceId": 1001
}
```

## 엔티티

### Wallet

```text
id
userId
balance
status
createdAt
updatedAt
```

### WalletTransaction

```text
id
walletId
transactionType
amount
balanceAfter
referenceType
referenceId
createdAt
```

WalletStatus:

```text
ACTIVE
LOCKED
CLOSED
```

WalletTransactionType:

```text
DEPOSIT
WITHDRAW
```

## 도메인 규칙

Wallet:

```text
ACTIVE 상태에서만 변경 가능
차감 금액은 0보다 커야 함
잔액보다 큰 금액은 차감 불가
차감 후 잔액은 음수가 될 수 없음
증가 금액은 0보다 커야 함
```

## 소유권 규칙

외부 API는 인증 사용자 기준으로 처리한다.

```text
지갑 생성 요청의 userId는 X-User-Id와 같아야 한다.
잔액 조회는 지갑 소유자만 가능하다.
개발/시연용 충전 API도 지갑 소유자만 가능하다.
내부 송금용 withdraw/deposit API는 X-Internal-Request 또는 서비스 간 secret 검증 후 허용한다.
오픈뱅킹 충전/출금 반영도 내부 API로만 허용한다.
내부 API에서는 userId를 신뢰하지 않고 walletId와 reference 정보만 처리한다.
```

## 멱등성 규칙

wallet-service는 transfer-service의 retry와 네트워크 재시도에 대비해 reference 기반 멱등성을 가진다.
같은 규칙은 banking-service의 오픈뱅킹 충전/출금 반영에도 적용한다.

중복 기준:

```text
walletId
transactionType
referenceType
referenceId
```

DB 제약:

```text
UNIQUE(wallet_id, transaction_type, reference_type, reference_id)
```

처리 정책:

```text
같은 reference로 같은 요청이 다시 오면 기존 WalletTransaction 기준으로 성공 응답을 반환한다.
같은 reference로 amount가 다르면 409 Conflict를 반환한다.
referenceType/referenceId가 없는 잔액 변경 요청은 실패시킨다.
OPEN_BANKING_CHARGE referenceId는 bank_tran_id 또는 api_tran_id를 사용한다.
OPEN_BANKING_WITHDRAWAL referenceId는 bankingTransferId 또는 bank_tran_id를 사용한다.
```

## 트랜잭션 규칙

잔액 변경은 반드시 하나의 DB 트랜잭션 안에서 처리한다.

흐름:

```text
1. Wallet 조회
2. 상태 확인
3. 금액 검증
4. 잔액 변경
5. WalletTransaction 저장
6. commit
```

## 동시성

초기 구현:

```text
DB 트랜잭션 + pessimistic lock 또는 Redis lock 중 하나 선택
```

권장 구현:

```text
Redis lock은 transfer-service에서 지갑 단위로 잡는다.
wallet-service에서는 DB row lock으로 마지막 방어선을 둔다.
```

Repository 예시:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select w from Wallet w where w.id = :walletId")
Optional<Wallet> findByIdForUpdate(Long walletId);
```

## 구현 순서

1. `Wallet` 엔티티 작성
2. `WalletTransaction` 엔티티 작성
3. enum 작성
4. Repository 작성
5. Wallet 도메인 메서드 작성
6. WalletApplicationService 작성
7. Controller 작성
8. 에러 코드 작성
9. 단위 테스트 작성
10. 동시성 테스트 작성

## 테스트

필수 테스트:

```text
지갑 생성 성공
같은 userId로 중복 지갑 생성 실패
충전 성공
같은 reference로 충전 재요청 시 중복 증가 없음
차감 성공
같은 reference로 차감 재요청 시 중복 차감 없음
같은 reference에 다른 amount 요청 시 409
잔액 부족 차감 실패
LOCKED 지갑 차감 실패
차감 시 WalletTransaction 저장
타인 지갑 조회 실패
동시 차감 시 잔액 음수 방지
```
