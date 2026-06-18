# Service Flow

PayFlow MVP는 동기 HTTP 호출로 핵심 결제 흐름을 완성한다.

## 1. Signup

```text
Client
-> api-gateway
-> user-service
   -> users 생성
   -> wallet-service /internal/wallets 호출
      -> wallets 생성
```

완료 조건:

사용자와 지갑이 생성된다.

## 2. Bank Deposit

API:

```text
GET  /api/bank/accounts
POST /api/bank/accounts
POST /api/bank/deposits
GET  /api/bank/transfers/{bankingTransferId}
```

흐름:

```text
Client
-> banking-service
   -> bank_accounts 검증
   -> banking_transfers REQUESTED 생성
   -> mock bank 처리
   -> wallet-service /internal/wallets/{userId}/credit 호출
   -> banking_transfers SUCCEEDED 변경
```

멱등성:

`banking_transfers.idempotency_key`, `request_hash`로 중복 요청을 제어한다.

## 3. Transfer

API:

```text
POST /api/transfers
GET  /api/transfers/{transferId}
GET  /api/transfers
```

Current implementation:

```text
Client
-> transfer-service
   -> create transfers row as REQUESTED
   -> acquire Redis lock: transfer:wallet-lock:{senderWalletId}
   -> call wallet-service sender debit
   -> call wallet-service receiver credit
   -> mark transfer SUCCEEDED
   -> store transfer.completed in outbox_events
   -> OutboxEventRelay publishes transfer.completed to Kafka
   -> ledger-service consumes transfer.completed
      -> deduplicate by transfer_id
      -> store one ledger_entries row and two ledger_lines rows
```

Failure event flow:

```text
transfer-service
-> mark transfer FAILED or COMPENSATION_REQUIRED
-> store transfer.failed in outbox_events
-> OutboxEventRelay publishes transfer.failed to Kafka
-> ledger-service consumes transfer.failed
   -> deduplicate by transfer_id
   -> store transfer_failure_events row with status and failureReason
```

Compensation refund flow:

```text
GET /api/transfers/compensations
GET /api/transfers/compensations/{transferId}
POST /api/transfers/compensations/{transferId}/refund

transfer-service
-> lock transfer row
-> require status COMPENSATION_REQUIRED
-> wallet-service sender deposit
   referenceType = TRANSFER_COMPENSATION
   referenceId = {transferId}
-> mark transfer COMPENSATED

If refund deposit fails:

```text
-> keep transfer COMPENSATION_REQUIRED
-> increment compensationRetryCount
-> store compensationFailureReason
-> return 502 COMPENSATION_REFUND_FAILED
-> allow later retry
```

Outbox relay rules:

```text
load PENDING/FAILED events
-> claim as PROCESSING
-> Kafka publish success: PUBLISHED
-> Kafka publish failure: FAILED and retryCount + 1
-> retryCount >= maxRetries: skip publishing
-> stale PROCESSING: recover to FAILED and allow retry
```

Outbox monitoring API:

```text
GET /api/transfers/outbox/summary
```

흐름:

```text
Client
-> transfer-service
   -> transfers REQUESTED 생성
   -> wallet-service sender debit 호출
   -> wallet-service receiver credit 호출
   -> transfers SUCCEEDED 변경
   -> ledger-service /internal/ledger/entries 호출
```

실패 처리:

출금 전 실패는 `FAILED`로 종료한다.

출금 후 입금 실패는 출금 거래 ID를 남기고 `FAILED`로 기록한다.

## 4. Family Link

API:

```text
POST /api/families/links
GET  /api/families/children
```

흐름:

```text
Parent
-> reward-service
   -> parent role 확인
   -> child role 확인
   -> parent_child_links ACTIVE 생성
```

## 5. Mission Reward

API:

```text
POST  /api/missions
GET   /api/missions
GET   /api/missions/{missionId}
PATCH /api/missions/{missionId}/submit
PATCH /api/missions/{missionId}/approve
PATCH /api/missions/{missionId}/reject
POST  /api/missions/{missionId}/pay
```

흐름:

```text
Parent
-> mission CREATED 생성

Child
-> mission SUBMITTED 변경

Parent
-> mission APPROVED 변경
-> reward-service pay
   -> transfer-service POST /transfers
      idempotencyKey = reward-payment-{missionId}
   -> paid_transfer_id 저장
   -> mission PAID 변경
```

## 6. Cashbook

API:

```text
GET /api/cashbook/children/{childUserId}/summary
GET /api/cashbook/children/{childUserId}/entries
```

흐름:

```text
reward-service
-> reward_tasks PAID 조회
-> wallet_transactions 조회 결과와 합쳐 최근 돈 기록 반환
```

## 7. Ledger

Current implementation:

```text
transfer.completed Kafka event
-> ledger-service
   -> deduplicate by transfer_id
   -> store ledger_entries row
   -> store DEBIT/CREDIT ledger_lines rows

transfer.failed Kafka event
-> ledger-service
   -> deduplicate by transfer_id
   -> store transfer_failure_events row
```

Failure lookup API:

```text
GET /api/ledgers/transfer-failures
GET /api/ledgers/transfer-failures/{transferId}
```

흐름:

```text
transfer-service or reward-service
-> ledger-service /internal/ledger/entries
   -> source_type, source_id 중복 확인
   -> debit 합계와 credit 합계 확인
   -> ledger_entries 저장
   -> ledger_lines 저장
```

규칙:

같은 source는 한 번만 저장한다.

차변과 대변 합계는 항상 같아야 한다.

## State Models

Mission:

```text
CREATED -> SUBMITTED -> APPROVED -> PAID
CREATED -> CANCELED
SUBMITTED -> REJECTED
```

Transfer:

```text
REQUESTED -> PROCESSING -> SUCCEEDED
REQUESTED -> PROCESSING -> FAILED
REQUESTED -> PROCESSING -> COMPENSATION_REQUIRED -> COMPENSATED
```

Banking transfer:

```text
REQUESTED -> SUCCEEDED
REQUESTED -> FAILED
```
