# API Spec

PayFlow MVP API는 회원, 지갑, 은행 충전, 송금, 가족 미션 보상, 원장 조회만 다룬다.

## Common

인증이 필요한 API는 `Authorization: Bearer {accessToken}`을 사용한다.

금액은 정수 원화 단위다.

충전, 송금, 보상 지급은 멱등하게 처리한다.

에러 응답:

```json
{
  "code": "ERROR_CODE",
  "message": "error message",
  "traceId": "trace-id"
}
```

## Gateway Routes

```text
/api/auth/**      -> user-service
/api/users/**     -> user-service
/api/wallets/**   -> wallet-service
/api/bank/**      -> banking-service
/api/payments/**  -> banking-service
/api/transfers/** -> transfer-service
/api/ledgers/**   -> ledger-service
/api/families/**  -> reward-service
/api/missions/**  -> reward-service
/api/cashbook/**  -> reward-service
```

## Auth API

### POST /api/auth/signup

```json
{
  "email": "parent@example.com",
  "password": "password123!",
  "name": "Parent",
  "role": "PARENT"
}
```

응답:

```json
{
  "userId": 1,
  "email": "parent@example.com",
  "name": "Parent",
  "role": "PARENT"
}
```

### POST /api/auth/login

```json
{
  "email": "parent@example.com",
  "password": "password123!"
}
```

응답:

```json
{
  "accessToken": "jwt",
  "user": {
    "userId": 1,
    "name": "Parent",
    "role": "PARENT",
    "status": "ACTIVE"
  }
}
```

### GET /api/users/me

내 사용자 정보를 조회한다.

## Wallet API

### GET /api/wallets/me

내 지갑 잔액을 조회한다.

응답:

```json
{
  "walletId": 100,
  "userId": 1,
  "balance": 50000,
  "currency": "KRW",
  "status": "ACTIVE"
}
```

### GET /api/wallets/me/transactions

내 지갑 거래 이력을 조회한다.

## Bank API

### GET /api/bank/accounts

연결 계좌 목록을 조회한다.

### POST /api/bank/accounts

```json
{
  "bankCode": "004",
  "accountNumber": "1234567890",
  "accountHolderName": "Parent"
}
```

응답:

```json
{
  "bankAccountId": 1,
  "bankCode": "004",
  "accountNumberMasked": "123-****-7890",
  "accountHolderName": "Parent",
  "status": "ACTIVE"
}
```

### POST /api/bank/deposits

Header:

`Idempotency-Key: 20260615-user1-deposit-001`

```json
{
  "bankAccountId": 1,
  "amount": 50000
}
```

응답:

```json
{
  "bankingTransferId": 1001,
  "transferType": "CHARGE",
  "bankAccountId": 1,
  "walletId": 10,
  "amount": 50000,
  "status": "COMPLETED",
  "walletTransactionId": 9001,
  "failureReason": null,
  "compensationRetryCount": 0,
  "compensationFailureReason": null,
  "compensatedAt": null
}
```

### POST /api/bank/withdrawals

Header:

`Idempotency-Key: 20260619-user1-withdrawal-001`

```json
{
  "bankAccountId": 1,
  "amount": 10000
}
```

Response:

```json
{
  "bankingTransferId": 1002,
  "transferType": "WITHDRAWAL",
  "bankAccountId": 1,
  "walletId": null,
  "amount": 10000,
  "status": "COMPENSATION_REQUIRED",
  "walletTransactionId": null,
  "failureReason": "OpenBanking deposit transfer is no-permission in this environment. Wallet withdrawal requires compensation.",
  "compensationRetryCount": 0,
  "compensationFailureReason": null,
  "compensatedAt": null
}
```

### GET /api/bank/transfers/{bankingTransferId}

충전 처리 상태를 조회한다.

### Open Banking Account Connection

#### GET /api/bank/openbanking/authorize-url

Returns an Open Banking authorization URL and signed `state`.

#### POST /api/bank/openbanking/callback

Exchanges authorization `code`, stores encrypted user token, calls `user/me`, and syncs linked accounts.

#### POST /api/bank/openbanking/accounts/sync

Syncs linked accounts using the stored encrypted user Open Banking token.

#### POST /api/bank/transfers/{bankingTransferId}/result-check

Checks Open Banking transfer result and finalizes `BANK_PROCESSING` or `UNKNOWN` banking transfers.

#### POST /api/bank/transfers/{bankingTransferId}/compensate

Refunds a `WITHDRAWAL` transfer in `COMPENSATION_REQUIRED` state back to the wallet.
Successful compensation changes status to `COMPENSATED`; failed compensation keeps the transfer in `COMPENSATION_REQUIRED`
and increments `compensationRetryCount`.

Banking transfer statuses:

```text
REQUESTED
WALLET_WITHDRAWING
BANK_PROCESSING
BANK_SUCCEEDED
WALLET_REFLECTING
COMPLETED
SUCCEEDED
FAILED
UNKNOWN
COMPENSATION_REQUIRED
COMPENSATED
```

## Transfer API

### POST /api/transfers

Header:

`Idempotency-Key: 20260615-user1-transfer-001`

```json
{
  "receiverUserId": 2,
  "amount": 10000
}
```

응답:

```json
{
  "transferId": 5000,
  "senderUserId": 1,
  "receiverUserId": 2,
  "amount": 10000,
  "status": "SUCCEEDED"
}
```

### GET /api/transfers/{transferId}

송금 상세를 조회한다.

### GET /api/transfers

내 송금 목록을 조회한다.

### GET /api/transfers/compensations

Returns transfers in `COMPENSATION_REQUIRED` state.

### GET /api/transfers/compensations/{transferId}

Returns one compensation-required transfer.

### POST /api/transfers/compensations/{transferId}/refund

Refunds a `COMPENSATION_REQUIRED` transfer back to the sender wallet.

Rules:

- Wallet deposit uses `referenceType=TRANSFER_COMPENSATION`.
- Wallet deposit uses `referenceId={transferId}` for idempotency.
- Successful refund changes transfer status to `COMPENSATED`.
- Refund failure keeps transfer status as `COMPENSATION_REQUIRED`, records retry metadata, and returns
  `502 Bad Gateway` with `code=COMPENSATION_REFUND_FAILED`.
- Successful responses and later compensation lookups include `compensationRetryCount`,
  `compensationFailureReason`, and `compensatedAt`.

### GET /api/transfers/outbox/summary

Returns operational summary for transfer-service outbox publishing state.

Response:

```json
{
  "totalCount": 10,
  "statusCounts": [
    {
      "status": "PENDING",
      "count": 2
    },
    {
      "status": "PROCESSING",
      "count": 1
    },
    {
      "status": "PUBLISHED",
      "count": 6
    },
    {
      "status": "FAILED",
      "count": 1
    }
  ],
  "retryableFailureCount": 1,
  "retryExhaustedCount": 0,
  "oldestPendingEventAgeSeconds": 42,
  "oldestPendingEventCreatedAt": "2026-06-18T21:00:00"
}
```

## Family API

### POST /api/families/links

부모가 자녀와 연결한다.

```json
{
  "childUserId": 2
}
```

### GET /api/families/children

부모에게 연결된 자녀 목록을 조회한다.

## Mission API

### POST /api/missions

부모가 자녀에게 미션을 생성한다.

```json
{
  "childUserId": 2,
  "title": "Read a book",
  "description": "Read for 30 minutes",
  "rewardAmount": 3000
}
```

### GET /api/missions

내 역할 기준 미션 목록을 조회한다.

Query:

`role=parent|child`, `status=CREATED|SUBMITTED|APPROVED|PAID|REJECTED|CANCELED`

### GET /api/missions/{missionId}

미션 상세를 조회한다.

### PATCH /api/missions/{missionId}/submit

자녀가 미션을 제출한다.

### PATCH /api/missions/{missionId}/approve

부모가 미션을 승인한다.

### PATCH /api/missions/{missionId}/reject

부모가 미션을 반려한다.

```json
{
  "reason": "Please try again after finishing the task."
}
```

### POST /api/missions/{missionId}/pay

승인된 미션 보상을 지급한다.

처리 규칙:

`reward-payment-{missionId}` 멱등키로 transfer-service에 송금을 요청한다.

## Cashbook API

### GET /api/cashbook/parent/summary

부모의 현재 지갑 잔액, 이번 달 지급 완료 보상 합계, 승인 대기 미션 수를 조회한다.

Response:

```json
{
  "walletId": 1,
  "creditBalance": 50000,
  "monthlyRewardPaid": 12000,
  "pendingApprovalCount": 3
}
```

### GET /api/cashbook/children/{childUserId}/summary

자녀 지갑 잔액과 지급 완료 미션 요약을 조회한다.

### GET /api/cashbook/children/{childUserId}/entries

자녀의 최근 돈 기록을 조회한다.

## Ledger API

Current implemented ledger APIs:

### GET /api/ledgers/transfer-failures

Returns failure tracking rows stored by ledger-service after consuming `transfer.failed` Kafka events.

Response:

```json
[
  {
    "transferId": 200,
    "senderUserId": 1,
    "receiverUserId": 2,
    "amount": 3000,
    "status": "FAILED",
    "failureReason": "wallet timeout",
    "createdAt": "2026-06-18T21:00:00"
  }
]
```

### GET /api/ledgers/transfer-failures/{transferId}

Returns one failure tracking row by `transferId`.

Response:

```json
{
  "transferId": 200,
  "senderUserId": 1,
  "receiverUserId": 2,
  "amount": 3000,
  "status": "FAILED",
  "failureReason": "wallet timeout",
  "createdAt": "2026-06-18T21:00:00"
}
```

Implemented ledger entry lookup APIs:

### GET /api/ledgers/entries

최근 원장 엔트리 100건을 조회한다. 송금, Toss PG 충전, Toss PG 취소가 같은 응답 모델로 내려온다.

Response:

```json
[
  {
    "id": 501,
    "transferId": null,
    "sourceType": "TOSS_CHARGE",
    "sourceId": 3001,
    "entryType": "USER_WALLET_TOPUP",
    "senderUserId": null,
    "receiverUserId": 1,
    "amount": 50000,
    "createdAt": "2026-06-20T16:30:00",
    "lines": [
      {
        "id": 1001,
        "userId": null,
        "accountCode": "PG_CASH",
        "type": "DEBIT",
        "amount": 50000
      },
      {
        "id": 1002,
        "userId": 1,
        "accountCode": "USER_WALLET",
        "type": "CREDIT",
        "amount": 50000
      }
    ]
  }
]
```

### GET /api/ledgers/entries/{entryId}

원장 엔트리 상세를 조회한다.

### POST /ledgers/internal/payment-charge

서비스 간 내부 호출 전용 API다. banking-service가 Toss 승인/취소 후 지갑 반영에 성공하면 원장에 이중분개를 남긴다.

Request:

```json
{
  "sourceType": "TOSS_CHARGE",
  "sourceId": 3001,
  "entryType": "USER_WALLET_TOPUP",
  "userId": 1,
  "amount": 50000
}
```

Allowed combinations:

```text
TOSS_CHARGE + USER_WALLET_TOPUP
TOSS_CANCEL + PG_CANCEL
```

Legacy planned names kept for old notes:

### GET /api/ledger/entries

내 원장 기록을 조회한다.

### GET /api/ledger/entries/{entryId}

원장 상세를 조회한다.

## Internal APIs

외부에 노출하지 않는다.

```text
POST /internal/wallets
POST /internal/wallets/{userId}/credit
POST /internal/wallets/{userId}/debit
GET  /internal/users/{userId}
POST /ledgers/internal/payment-charge
```
## Toss Payment API

### POST /api/payments/toss/charges

Toss 결제 위젯을 열기 전에 내부 충전 요청과 Toss `orderId`를 생성한다.

Header:

`Idempotency-Key: 20260620-user1-toss-charge-001`

```json
{
  "amount": 50000,
  "orderName": "PayFlow 크레딧 충전"
}
```

Response:

```json
{
  "chargeId": 3001,
  "providerCode": "TOSS_PAYMENTS",
  "orderId": "payflow-charge-3001-20260620",
  "orderName": "PayFlow 크레딧 충전",
  "amount": 50000,
  "currency": "KRW",
  "status": "READY",
  "customerKey": "user-1"
}
```

### POST /api/payments/toss/confirm

Toss 승인 성공 시 wallet-service 입금 이후 ledger-service에 `TOSS_CHARGE + USER_WALLET_TOPUP` 원장을 멱등 기록한다.

Toss 결제 승인 callback 이후 서버에서 Toss 승인 API를 호출하고, 성공하면 wallet-service에 입금을 요청한다.

```json
{
  "paymentKey": "tgen_20260620_xxx",
  "orderId": "payflow-charge-3001-20260620",
  "amount": 50000
}
```

Response:

```json
{
  "chargeId": 3001,
  "paymentKey": "tgen_20260620_xxx",
  "amount": 50000,
  "status": "COMPLETED",
  "walletTransactionId": 9100,
  "receiptUrl": "https://dashboard.tosspayments.com/receipt/..."
}
```

### GET /api/payments/toss/charges/{chargeId}

내부 충전 상태와 Toss 결제 상태를 함께 조회한다.

### POST /api/payments/toss/webhook

Toss 결제 상태 변경 웹훅을 수신한다. 동일 웹훅은 `event_idempotency_key`로 중복 처리하지 않는다.
`TOSS_PAYMENTS_WEBHOOK_SECRET`이 설정된 환경에서는 `X-Toss-Signature` 헤더를 검증한다.

### POST /api/payments/toss/payments/{paymentKey}/cancel

Toss 취소와 wallet-service 차감이 성공하면 ledger-service에 `TOSS_CANCEL + PG_CANCEL` 원장을 멱등 기록한다.

Toss 결제를 취소하고 내부 충전 상태와 지갑/원장 보상 필요 여부를 갱신한다.

```json
{
  "cancelReason": "사용자 요청",
  "cancelAmount": 50000
}
```

### GET /api/payments/toss/payments/{paymentKey}

Toss 결제 조회 API를 호출해서 내부 상태와 외부 상태 차이를 보정한다.

### GET /api/payments/toss/operations/summary

Toss 충전 운영 상태 카운트를 조회한다.

### GET /api/payments/toss/operations/compensations

`COMPENSATION_REQUIRED` 상태의 Toss 충전 목록을 조회한다.

### POST /api/payments/toss/charges/{chargeId}/compensate

Toss 결제는 성공했지만 wallet 입금이 실패한 충전 건을 재입금한다.
성공하면 `COMPLETED`, 실패하면 `COMPENSATION_REQUIRED`를 유지하고 retry metadata를 기록한다.

## Toss Ledger Compensation API

### GET /api/payments/toss/operations/ledger-compensations

지갑 반영은 완료됐지만 ledger-service 원장 기록이 실패한 Toss 충전 목록을 조회한다.

### POST /api/payments/toss/charges/{chargeId}/ledger-compensate

지갑 반영이 완료된 Toss 충전 건의 원장 기록만 재처리한다. 성공하면 `ledgerRecorded=true`, 실패하면 `ledgerFailureReason`과 `ledgerRetryCount`를 갱신한다.

`GET /api/payments/toss/operations/summary` 응답에는 `ledgerCompensationRequiredCount`가 포함된다.

Toss charge responses and summary responses include ledger fields:

```json
{
  "ledgerRecorded": false,
  "ledgerRecordType": "TOSS_CHARGE",
  "ledgerFailureReason": "ledger-service timeout",
  "ledgerRetryCount": 1
}
```
