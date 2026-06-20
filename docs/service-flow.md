# Service Flow

PayFlow는 동기 HTTP 호출과 Kafka 이벤트를 함께 사용해 결제 흐름을 완성한다.

## 1. Signup

```text
Client
-> api-gateway (JWT 검증 없음)
-> user-service
   -> users 생성
   -> wallet-service /internal/wallets 호출
      -> wallets 생성
```

완료 조건: 사용자와 지갑이 함께 생성된다.

---

## 2. OpenBanking 계좌 연결 및 충전/출금

### 2-1. 오픈뱅킹 인가 URL 발급

```text
GET /api/bank/openbanking/authorize-url

Client
-> banking-service
   -> open_banking_authorizations PENDING 생성 (state 발급)
   -> 오픈뱅킹 인가 URL 반환
```

### 2-2. 오픈뱅킹 Callback 처리

```text
POST /api/bank/openbanking/callback

Client (오픈뱅킹 redirect 후)
-> banking-service
   -> state로 open_banking_authorizations 조회
   -> 오픈뱅킹 토큰 교환 API 호출
   -> open_banking_tokens 저장 (암호화)
   -> open_banking_authorizations COMPLETED 변경
```

### 2-3. 계좌 동기화

```text
POST /api/bank/openbanking/accounts/sync

Client
-> banking-service
   -> 오픈뱅킹 계좌 목록 API 호출 (액세스 토큰 사용)
   -> bank_accounts 저장/갱신
```

### 2-4. 오픈뱅킹 충전

API:

```text
POST /api/bank/deposits
GET  /api/bank/transfers/{bankingTransferId}
POST /api/bank/transfers/{bankingTransferId}/result-check
```

흐름:

```text
Client
-> banking-service
   -> bank_accounts 검증
   -> banking_transfers REQUESTED 생성 (bankTranId 발급)
   -> 오픈뱅킹 입금이체 API 호출 (banking_api_logs 기록)
      성공 (BANK_SUCCEEDED):
         -> wallet-service /internal/wallets/{userId}/credit 호출
         -> banking_transfers COMPLETED 변경
      처리중 (BANK_PROCESSING):
         -> banking_transfers BANK_PROCESSING 변경
         -> nextResultCheckAt 설정
         -> OpenBankingResultCheckScheduler가 결과 재조회
      실패 (BANK_FAILED):
         -> banking_transfers FAILED 변경
```

결과 재조회 스케줄러 (`OpenBankingResultCheckScheduler`):

```text
BANK_PROCESSING 상태이고 nextResultCheckAt이 지난 거래 조회
-> 오픈뱅킹 이체결과조회 API 호출
-> BANK_SUCCEEDED: wallet credit 호출 -> COMPLETED
-> BANK_PROCESSING: nextResultCheckAt 지수 백오프 연장 (최대 16분)
-> BANK_FAILED: FAILED 변경
```

### 2-5. 오픈뱅킹 출금

```text
POST /api/bank/withdrawals

Client
-> banking-service
   -> banking_transfers REQUESTED 생성
   -> 오픈뱅킹 출금이체 API 호출
      성공: wallet-service debit -> COMPLETED
      실패 (지갑 차감 후 은행 실패): COMPENSATION_REQUIRED
         -> POST /api/bank/transfers/{id}/compensate 로 지갑 재입금
```

멱등성: `banking_transfers.idempotency_key`, `request_hash`

---

## 3. Toss PG 충전

API:

```text
POST /api/payments/toss/charges
POST /api/payments/toss/confirm
POST /api/payments/toss/webhook
GET  /api/payments/toss/payments/{paymentKey}
POST /api/payments/toss/payments/{paymentKey}/cancel
GET  /api/payments/toss/charges/{chargeId}
GET  /api/payments/toss/operations/summary
GET  /api/payments/toss/operations/compensations
POST /api/payments/toss/charges/{chargeId}/compensate
GET  /api/payments/toss/operations/ledger-compensations
POST /api/payments/toss/charges/{chargeId}/ledger-compensate
```

흐름:

```text
Client
-> POST /api/payments/toss/charges
   -> payment_charges READY 생성
   -> toss_payment_orders READY 생성 (tossOrderId 발급)
   -> orderId, customerKey 반환 (프론트에서 위젯 열기)

사용자 결제 완료 후

Client
-> POST /api/payments/toss/confirm (paymentKey, orderId, amount)
   -> banking-service
      -> Toss 승인 API 호출
      -> payment_charges PAYMENT_APPROVED 변경
      -> payment_charges WALLET_REFLECTING 변경
      -> wallet-service /internal/wallets/{userId}/credit 호출
      -> payment_charges COMPLETED 변경
      -> ledger-service /ledgers/internal/payment-charge 호출
         (TOSS_CHARGE + USER_WALLET_TOPUP)
         성공: payment_charges ledgerRecorded=true
         실패: payment_charges ledgerRecorded=false, ledgerFailureReason 기록
```

웹훅:

```text
Toss
-> POST /api/payments/toss/webhook
   -> event_idempotency_key로 중복 확인
   -> toss_payment_events 저장
   -> toss_payment_orders 상태 갱신
```

취소:

```text
POST /api/payments/toss/payments/{paymentKey}/cancel

-> Toss 취소 API 호출
-> wallet-service debit 호출
-> payment_charges CANCELED/PARTIAL_CANCELED 변경
-> ledger-service /ledgers/internal/payment-charge 호출
   (TOSS_CANCEL + PG_CANCEL)
```

보상 흐름:

지갑 입금 실패 시 `COMPENSATION_REQUIRED` → `/charges/{id}/compensate`로 재입금

원장 기록 실패 시 `ledgerRecorded=false` → `/charges/{id}/ledger-compensate`로 재기록

멱등성: `payment_charges.idempotency_key`, `request_hash`

---

## 4. Transfer (지갑 간 송금)

API:

```text
POST /api/transfers
GET  /api/transfers/{transferId}
GET  /api/transfers
GET  /api/transfers/compensations
GET  /api/transfers/compensations/{transferId}
POST /api/transfers/compensations/{transferId}/refund
GET  /api/transfers/outbox/summary
```

흐름:

```text
Client
-> transfer-service
   -> transfers REQUESTED 생성
   -> Redis lock: transfer:wallet-lock:{senderWalletId} 획득
   -> transfers PROCESSING 변경
   -> wallet-service sender debit 호출
      -> senderWalletId 기록
   -> wallet-service receiver credit 호출
      -> receiverWalletId 기록
   -> transfers SUCCEEDED 변경
   -> outbox_events PENDING 저장 (transfer.completed)
   -> OutboxEventRelay (스케줄)
      -> PENDING → PROCESSING 클레임
      -> Kafka 발행 성공: PUBLISHED
      -> Kafka 발행 실패: FAILED, retryCount + 1

ledger-service (Kafka consumer)
-> transfer.completed 소비
   -> (source_type=TRANSFER, source_id=transferId) 중복 확인
   -> ledger_entries 저장
   -> DEBIT/CREDIT ledger_lines 2건 저장

transfer.failed 소비
   -> transfer_failure_events 저장 (중복 방지: UNIQUE transfer_id)
```

실패 처리:

```text
출금 전 실패 (Redis lock, 출금 호출 실패)
-> transfers FAILED
-> outbox_events에 transfer.failed 저장

출금 후 입금 실패
-> transfers COMPENSATION_REQUIRED
-> senderWalletId 기록 (보상 출금에 사용)
-> outbox_events에 transfer.failed 저장
```

보상 환불:

```text
POST /api/transfers/compensations/{transferId}/refund

-> transfers 상태가 COMPENSATION_REQUIRED 확인
-> wallet-service sender credit 호출
   referenceType = TRANSFER_COMPENSATION
   referenceId = {transferId}
-> transfers COMPENSATED 변경

환불 실패 시:
-> transfers COMPENSATION_REQUIRED 유지
-> compensationRetryCount + 1, compensationFailureReason 기록
-> 502 COMPENSATION_REFUND_FAILED 반환
-> 이후 재시도 가능
```

Outbox relay 규칙:

```text
PENDING/FAILED 이벤트 조회
-> PROCESSING 클레임
-> Kafka 발행 성공: PUBLISHED
-> Kafka 발행 실패: FAILED, retryCount + 1
-> retryCount >= maxRetries: 스킵
-> PROCESSING 상태로 stale된 이벤트: FAILED로 복구 후 재시도
```

---

## 5. Family Link

API:

```text
POST /api/families/links
GET  /api/families/children
GET  /api/families/parents
```

흐름:

```text
Parent
-> reward-service
   -> requestUserId의 role이 PARENT 확인
   -> childUserId의 role이 CHILD 확인 (user-service 조회)
   -> parent_child_links ACTIVE 생성
```

---

## 6. Mission Reward

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

상태 전이:

```text
CREATED -> SUBMITTED -> APPROVED -> PAID
CREATED -> CANCELED
SUBMITTED -> REJECTED -> SUBMITTED (재제출 가능)
```

보상 지급 흐름:

```text
Parent
-> POST /api/missions/{missionId}/pay
   -> reward-service
      -> mission이 APPROVED 상태 확인
      -> transfer-service POST /api/transfers
         idempotencyKey = reward-payment-{missionId}
      -> reward_tasks.transfer_id 저장
      -> mission PAID 변경

      지급 실패 시:
      -> reward_tasks.failureReason 기록
      -> mission 상태 APPROVED 유지
```

---

## 7. Cashbook

API:

```text
GET /api/cashbook/parent/summary
GET /api/cashbook/children/{childUserId}/summary
GET /api/cashbook/children/{childUserId}/entries
```

부모 요약:

```text
reward-service
-> wallet-service에서 부모 지갑 잔액 조회
-> 이번 달 PAID 미션 보상 합계 집계
-> APPROVED 상태(승인 대기) 미션 수 집계
```

자녀 요약/내역:

```text
reward-service
-> reward_tasks PAID 조회
-> wallet-service에서 자녀 wallet_transactions 조회
-> 통합해서 반환
```

---

## 8. Ledger

API:

```text
GET /api/ledgers/entries
GET /api/ledgers/entries/{entryId}
GET /api/ledgers/transfer-failures
GET /api/ledgers/transfer-failures/{transferId}
```

내부 기록 API (서비스 간 내부 호출 전용):

```text
POST /ledgers/internal/payment-charge
```

원장 기록 흐름:

송금 원장 (`TRANSFER`):
```text
transfer.completed Kafka 이벤트
-> ledger-service consumer
   -> (source_type=TRANSFER, source_id=transferId) 중복 확인
   -> ledger_entries 저장 (entry_type=TRANSFER)
   -> DEBIT ledger_line (송금자, USER_WALLET_OUT)
   -> CREDIT ledger_line (수신자, USER_WALLET_IN)
```

Toss 충전 원장 (`TOSS_CHARGE`):
```text
banking-service (Toss confirm 완료 후)
-> POST /ledgers/internal/payment-charge
   sourceType=TOSS_CHARGE, entryType=USER_WALLET_TOPUP
   -> ledger_entries 저장
   -> DEBIT ledger_line (userId=null, PG_CASH)
   -> CREDIT ledger_line (userId, USER_WALLET_IN)
```

Toss 취소 원장 (`TOSS_CANCEL`):
```text
banking-service (취소 완료 후)
-> POST /ledgers/internal/payment-charge
   sourceType=TOSS_CANCEL, entryType=PG_CANCEL
   -> ledger_entries 저장
   -> DEBIT ledger_line (userId, USER_WALLET_OUT)
   -> CREDIT ledger_line (userId=null, PG_CASH)
```

규칙:
- 같은 source는 `UNIQUE(source_type, source_id)`로 한 번만 저장한다.
- 차변(DEBIT) 합계와 대변(CREDIT) 합계는 항상 같아야 한다.

---

## State Models

### Transfer

```text
REQUESTED -> PROCESSING -> SUCCEEDED
REQUESTED -> PROCESSING -> FAILED
REQUESTED -> PROCESSING -> COMPENSATION_REQUIRED -> COMPENSATED
```

### BankingTransfer (오픈뱅킹)

```text
REQUESTED -> BANK_PROCESSING -> BANK_SUCCEEDED -> WALLET_REFLECTING -> COMPLETED
REQUESTED -> BANK_SUCCEEDED -> WALLET_REFLECTING -> COMPLETED
REQUESTED -> BANK_PROCESSING -> FAILED
REQUESTED -> BANK_FAILED -> FAILED
출금 후 지갑 차감 실패: COMPENSATION_REQUIRED -> COMPENSATED
출금 후 지갑 반영 중 실패: WALLET_WITHDRAWING -> COMPENSATION_REQUIRED
```

### PaymentCharge (Toss PG)

```text
READY -> PAYMENT_APPROVED -> WALLET_REFLECTING -> COMPLETED
READY -> FAILED
COMPLETED -> CANCELED / PARTIAL_CANCELED
WALLET_REFLECTING 실패: COMPENSATION_REQUIRED -> (보상 재시도 후) COMPLETED
```

### Mission

```text
CREATED -> SUBMITTED -> APPROVED -> PAID
CREATED -> CANCELED
SUBMITTED -> REJECTED -> SUBMITTED (재제출)
```

### OutboxEvent

```text
PENDING -> PROCESSING -> PUBLISHED
PENDING -> PROCESSING -> FAILED (재시도)
```
