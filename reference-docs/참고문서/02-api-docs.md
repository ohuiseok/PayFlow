# API Docs

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

이 문서는 포트폴리오용 API 요약 문서입니다. 상세 구현은 각 서비스의 controller와 `docs/api-spec.md`를 함께 참고합니다.

## Common Rules

인증이 필요한 API는 다음 헤더를 사용합니다.

```http
Authorization: Bearer {accessToken}
```

중복 지급/중복 송금을 막아야 하는 요청은 다음 헤더를 사용합니다.

```http
Idempotency-Key: unique-business-request-key
```

에러 응답 기본 형태:

```json
{
  "code": "ERROR_CODE",
  "message": "error message",
  "traceId": "trace-id"
}
```

## Gateway Routes

외부 API는 Gateway에서 `/api` prefix로 진입하고 내부 서비스로 라우팅됩니다.

| External Path | Target Service |
| --- | --- |
| `/api/users/**` | user-service |
| `/api/auth/**` | user-service |
| `/api/wallets/**` | wallet-service |
| `/api/bank/**` | banking-service |
| `/api/transfers/**` | transfer-service |
| `/api/families/**` | reward-service |
| `/api/missions/**` | reward-service |
| `/api/cashbook/**` | reward-service |
| `/api/ledgers/**` | ledger-service |
| `/api/settlements/**` | settlement-service |

## Auth/User

### POST /api/users

회원가입 후 사용자와 지갑 생성 흐름을 시작합니다.

```json
{
  "phoneNumber": "01011112222",
  "password": "password123!",
  "name": "Agency",
  "inviteCode": "PAYFLOW-PARENT-2024"
}
```

### POST /api/users/login

로그인 후 access token과 사용자 정보를 반환합니다.

```json
{
  "phoneNumber": "01011112222",
  "password": "password123!"
}
```

### GET /api/users/me

Gateway가 주입한 사용자 식별자를 기준으로 내 정보를 조회합니다.

## Wallet

### GET /api/wallets/users/{userId}

사용자 지갑을 조회합니다. 송금/보상/충전 서비스가 내부적으로 사용하는 핵심 조회 API입니다.

### POST /api/wallets/{walletId}/deposit

지갑에 금액을 입금합니다.

```json
{
  "amount": 50000,
  "referenceType": "OPEN_BANKING_CHARGE",
  "referenceId": "bank-tran-id"
}
```

### POST /api/wallets/{walletId}/withdraw

지갑에서 금액을 출금합니다.

```json
{
  "amount": 10000,
  "referenceType": "TRANSFER_DEBIT",
  "referenceId": "transfer-id"
}
```

wallet-service는 `referenceType` + `referenceId`로 같은 원천 거래가 잔액에 중복 반영되는 것을 막습니다.

## Banking

### POST /api/bank/accounts

연결 계좌를 등록합니다.

```json
{
  "bankCode": "004",
  "accountNumber": "1234567890",
  "accountHolderName": "Agency"
}
```

### GET /api/bank/accounts

현재 사용자에게 연결된 계좌 목록을 조회합니다.

### POST /api/bank/deposits

은행 계좌에서 PayFlow 지갑으로 충전합니다.

```http
Idempotency-Key: deposit-parent-20260620-001
```

```json
{
  "bankAccountId": 1,
  "amount": 50000
}
```

핵심 규칙:

- 같은 idempotency key와 같은 body는 기존 결과를 반환합니다.
- 같은 idempotency key와 다른 body는 충돌로 처리합니다.
- 지갑 반영은 bank success가 확정된 뒤 수행합니다.

### POST /api/bank/withdrawals

지갑에서 은행 계좌로 출금 요청을 생성합니다. 현재 환경에서는 Open Banking deposit transfer 권한 제약 때문에 보상 필요 상태를 명시적으로 모델링합니다.

### POST /api/bank/transfers/{bankingTransferId}/result-check

`BANK_PROCESSING`, `UNKNOWN`처럼 최종 결과가 모호한 거래를 재조회합니다.

### POST /api/bank/transfers/{bankingTransferId}/compensate

은행 출금 실패 또는 권한 제약으로 격리된 거래를 지갑 환불로 보상합니다.

## Transfer

### POST /api/transfers

사용자 간 지갑 송금을 요청합니다.

```http
Idempotency-Key: transfer-parent-child-20260620-001
```

```json
{
  "receiverUserId": 2,
  "amount": 10000
}
```

처리 흐름:

```text
transfer REQUESTED 생성
-> sender wallet Redis lock 획득
-> sender wallet withdraw
-> receiver wallet deposit
-> transfer SUCCEEDED
-> outbox event 저장
-> Kafka publish
-> ledger-service consume
```

### GET /api/transfers/{transferId}

송금 상세를 조회합니다.

### GET /api/transfers

현재 사용자의 송금 목록을 조회합니다.

### GET /api/transfers/compensations

보상 처리가 필요한 송금 목록을 조회합니다.

### POST /api/transfers/compensations/{transferId}/refund

출금 이후 입금 실패 등으로 `COMPENSATION_REQUIRED`가 된 송금을 환불합니다.

### GET /api/transfers/outbox/summary

transfer-service outbox 발행 상태를 운영 관점에서 조회합니다.

## Family/Mission/Reward

### POST /api/families/links

기관 담당자가 청년 참여자를 연결합니다.

```json
{
  "childUserId": 2
}
```

### GET /api/families/children

기관 담당자에게 연결된 청년 참여자 목록을 조회합니다.

### POST /api/missions

기관 담당자가 청년 참여자에게 미션을 생성합니다.

```json
{
  "childUserId": 2,
  "title": "Read a book",
  "description": "Read for 30 minutes",
  "rewardAmount": 3000
}
```

### PATCH /api/missions/{missionId}/submit

청년이 미션 완료를 제출합니다.

### PATCH /api/missions/{missionId}/approve

기관 담당자가 미션을 승인합니다.

### PATCH /api/missions/{missionId}/reject

기관 담당자가 미션을 반려합니다.

```json
{
  "reason": "Please try again after finishing the task."
}
```

### POST /api/missions/{missionId}/pay

승인된 미션에 대한 보상을 지급합니다.

지원금 지급은 transfer-service에 송금을 요청하며 idempotency key는 아래 규칙을 사용합니다.

```text
reward-payment-{missionId}
```

## Cashbook

### GET /api/cashbook/parent/summary

기관 지갑 잔액, 월 지급 보상, 승인 대기 미션 수를 조회합니다.

### GET /api/cashbook/children/{childUserId}/summary

청년의 지갑/미션 요약을 조회합니다.

### GET /api/cashbook/children/{childUserId}/entries

청년의 최근 돈 기록을 조회합니다.

## Ledger

### GET /api/ledgers/transfer-failures

`transfer.failed` Kafka 이벤트를 소비해 저장한 실패 추적 목록을 조회합니다.

### GET /api/ledgers/transfer-failures/{transferId}

특정 송금의 실패 추적 정보를 조회합니다.

## Settlement

### POST /api/settlements/daily/{businessDate}

ISO 날짜(`yyyy-MM-dd`)의 Toss PG 정산을 수동 실행합니다. 완료되었거나 차이가 있는 실행이 이미 존재하면 기존 결과를 반환합니다. 현재 JWT 인증은 적용되지만 관리자 역할 제한은 추가 과제입니다.

### GET /api/settlements/daily/{businessDate}

기준일의 거래 건수, 승인액, 취소액, 수수료, 예상 순정산액, 원장 불일치 건수와 실행 상태를 조회합니다. 실행 이력이 없으면 404를 반환합니다.

```text
status = RUNNING | COMPLETED | WITH_DISCREPANCY | FAILED
expectedNetAmount = grossAmount - cancelAmount - feeAmount
```

## Internal API Contract

외부 포트폴리오에는 숨기되, 설계 설명에서는 아래 내부 호출을 언급하면 좋습니다.

```text
transfer-service -> wallet-service
reward-service   -> transfer-service
banking-service  -> wallet-service
transfer-service -> Kafka
ledger-service   <- Kafka
banking-service  -> payment.settlement (Kafka, outbox)
settlement-service <- payment.settlement
settlement-service -> ledger-service /ledgers/internal/payment-entry
```


