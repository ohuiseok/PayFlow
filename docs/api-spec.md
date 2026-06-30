# PayFlow API Spec

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

## Common

인증이 필요한 API는 `Authorization: Bearer {accessToken}`을 사용한다.

금액은 원화 정수 단위로 전달한다. 충전, 송금, 지원금 지급은 멱등하게 처리한다.

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
/api/settlements/** -> settlement-service
/api/families/**  -> reward-service
/api/missions/**  -> reward-service
/api/cashbook/**  -> reward-service
```

## Auth API

### POST /api/users

기관 담당자 가입은 초대 코드가 필요하다. 내부 역할은 `PARENT`로 저장된다.

```json
{
  "phoneNumber": "01011112222",
  "password": "password1234",
  "name": "Agency",
  "inviteCode": "PAYFLOW-PARENT-2024"
}
```

청년 참여자 가입은 초대 코드 없이 진행한다. 내부 역할은 `CHILD`로 저장된다.

```json
{
  "phoneNumber": "01033334444",
  "password": "password1234",
  "name": "Youth"
}
```

### POST /api/users/login

```json
{
  "phoneNumber": "01011112222",
  "password": "password1234"
}
```

응답:

```json
{
  "accessToken": "jwt",
  "tokenType": "Bearer",
  "expiresIn": 86400000
}
```

### GET /api/users/me

현재 로그인한 사용자 정보를 조회한다.

## Wallet API

### GET /api/wallets/me

내 지갑 잔액을 조회한다.

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

내 지갑 거래 내역을 조회한다. 기관 담당자 화면에서는 지원금 예산 기록, 청년 화면에서는 지원금 사용 내역으로 보여준다.

## Bank API

### GET /api/bank/accounts

연결 계좌 목록을 조회한다.

### POST /api/bank/accounts

```json
{
  "bankCode": "004",
  "accountNumber": "123456789012",
  "accountHolderName": "Agency"
}
```

### POST /api/bank/deposits

기관 담당자의 지원금 예산을 충전한다.

Header:

```text
Idempotency-Key: 20260626-agency1-charge-001
```

```json
{
  "bankAccountId": 1,
  "amount": 50000
}
```

### POST /api/bank/withdrawals

지갑 잔액을 연결 계좌로 출금한다.

Header:

```text
Idempotency-Key: 20260626-user1-withdrawal-001
```

```json
{
  "bankAccountId": 1,
  "amount": 10000
}
```

### Open Banking

```text
GET  /api/bank/openbanking/authorize-url
POST /api/bank/openbanking/callback
POST /api/bank/openbanking/accounts/sync
POST /api/bank/transfers/{bankingTransferId}/result-check
```

## Toss PG API

### POST /api/payments/toss/charges

기관 담당자의 지원금 예산을 Toss 결제로 충전하기 위한 주문을 생성한다.

```json
{
  "amount": 50000,
  "orderName": "PayFlow 지원금 충전"
}
```

### POST /api/payments/toss/confirm

Toss 결제 승인을 확정하고 지갑에 충전 금액을 반영한다.

### POST /api/payments/toss/webhook

Toss 웹훅 이벤트를 멱등하게 기록하고 결제 상태를 갱신한다.

### 운영 API

```text
GET  /api/payments/toss/operations/summary
GET  /api/payments/toss/operations/compensations
POST /api/payments/toss/charges/{chargeId}/compensate
GET  /api/payments/toss/operations/ledger-compensations
POST /api/payments/toss/charges/{chargeId}/ledger-compensate
```

## Transfer API

### POST /api/transfers

기관 지갑에서 청년 지갑으로 지원금을 송금할 때 사용한다. 일반 사용자 간 송금 기능도 같은 API를 재사용한다.

Header:

```text
Idempotency-Key: reward-payment-{missionId}
```

```json
{
  "receiverUserId": 2,
  "amount": 5000,
  "memo": "청년 금융 교육 참여 지원금"
}
```

### GET /api/transfers/{transferId}

송금 상태를 조회한다.

### Compensation API

```text
GET  /api/transfers/compensations
GET  /api/transfers/compensations/{transferId}
POST /api/transfers/compensations/{transferId}/refund
```

출금 후 입금 실패처럼 이미 돈이 움직인 중간 실패를 복구하기 위한 운영 API다.

## Settlement API

Toss PG 승인·취소 이벤트를 기준일별로 집계하고 `ledger-service`의 결제 원장과 대사한다. Gateway를 통과하므로 JWT 인증이 필요하다. 현재 별도 관리자 역할 검증은 구현되어 있지 않아 운영 전 권한 분리가 필요하다.

### POST /api/settlements/daily/{businessDate}

`businessDate`는 ISO 날짜(`yyyy-MM-dd`)다. 해당 날짜의 정산 배치를 실행한다. 이미 `COMPLETED` 또는 `WITH_DISCREPANCY`인 실행이 있으면 새 배치를 만들지 않고 기존 결과를 반환한다.

```json
{
  "id": 1,
  "businessDate": "2026-06-30",
  "status": "COMPLETED",
  "transactionCount": 2,
  "discrepancyCount": 0,
  "grossAmount": 10000,
  "cancelAmount": 1000,
  "feeAmount": 270,
  "expectedNetAmount": 8730,
  "completedAt": "2026-07-01T01:00:03"
}
```

집계식:

```text
grossAmount       = CHARGE 합계
cancelAmount      = CANCEL 합계
feeAmount         = grossAmount × feeRate (기본 0.027, 원 단위 HALF_UP)
expectedNetAmount = grossAmount - cancelAmount - feeAmount
```

### GET /api/settlements/daily/{businessDate}

기준일의 정산 결과를 조회한다. 실행 이력이 없으면 `404 Not Found`를 반환한다.

### 내부 원장 대사 API

정산 배치는 다음 내부 API로 거래별 원장을 조회한다. 외부 클라이언트용 API가 아니며 `X-Internal-Secret` 검증을 거친다.

```text
GET /ledgers/internal/payment-entry?sourceType=TOSS_CHARGE&sourceId={chargeId}
GET /ledgers/internal/payment-entry?sourceType=TOSS_CANCEL&sourceId={chargeId}
```

원장이 없으면 `MISSING_LEDGER`, 금액이 다르면 `AMOUNT_MISMATCH`, 일치하면 `MATCHED`로 기록한다.

## Policy Mission API

내부 경로는 기존 `/api/families`, `/api/missions`, `/api/cashbook`을 유지한다.

### POST /api/families/links

기관 담당자가 청년 참여자를 연결한다.

```json
{
  "childUserId": 2
}
```

### GET /api/families/children

기관 담당자에게 연결된 청년 참여자 목록을 조회한다.

### GET /api/families/parents

청년 참여자에게 연결된 기관 담당자 목록을 조회한다.

### POST /api/missions

기관 담당자가 청년 참여자에게 정책 미션을 생성한다.

```json
{
  "childUserId": 2,
  "title": "청년 금융 교육 참여",
  "description": "수료 화면 제출",
  "rewardAmount": 5000,
  "missionDate": "2026-06-26",
  "evidenceRequired": true
}
```

### GET /api/missions

내 역할 기준 정책 미션 목록을 조회한다.

### PATCH /api/missions/{missionId}/submit

청년 참여자가 정책 미션 완료 내용을 제출한다.

```json
{
  "submissionNote": "교육 수료 화면을 제출합니다."
}
```

### PATCH /api/missions/{missionId}/approve

기관 담당자가 제출된 정책 미션을 승인한다.

### PATCH /api/missions/{missionId}/reject

기관 담당자가 제출된 정책 미션을 반려한다.

```json
{
  "reason": "증빙 내용이 부족합니다."
}
```

### POST /api/missions/{missionId}/pay

승인된 정책 미션의 지원금을 지급한다. 내부적으로 `reward-payment-{missionId}` 멱등키로 `transfer-service`에 송금을 요청한다.

### GET /api/cashbook/parent/summary

기관 담당자의 지원금 예산 잔액, 이번 달 지급 완료 지원금 합계, 승인 대기 미션 수를 조회한다.

### GET /api/cashbook/children/{childUserId}/summary

청년 참여자의 지원금 잔액과 지급 완료 정책 미션 요약을 조회한다.

### GET /api/cashbook/children/{childUserId}/entries

청년 참여자의 최근 지원금 사용 내역을 조회한다.
