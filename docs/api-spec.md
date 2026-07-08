# PayFlow API 명세

> 최종 점검일: 2026-07-06
> 기준: API Gateway 라우팅, 7개 서비스의 컨트롤러 68개 엔드포인트, 요청·응답 DTO, 인증 필터

## 1. 기본 규칙

### 1.1 외부 진입점

- 기본 URL: `http://localhost:8080`
- 외부 클라이언트는 API Gateway의 `/api/**` 경로를 사용한다.
- Gateway는 `/api` 한 단계만 제거해 각 서비스의 컨트롤러로 전달한다.
- JSON 요청은 `Content-Type: application/json`을 사용한다.
- 날짜는 `yyyy-MM-dd`, 일시는 ISO-8601 형식을 사용한다.
- 금액은 원화 정수 단위이며 주요 송금·충전 API는 `1`~`10,000,000` 범위를 허용한다.

### 1.2 인증 표기

| 표기 | 의미 |
| --- | --- |
| 공개 | JWT 없이 Gateway 호출 가능 |
| JWT | `Authorization: Bearer {accessToken}` 필요 |
| PARENT | JWT 역할이 `PARENT`; 화면에서는 기관 담당자 |
| CHILD | JWT 역할이 `CHILD`; 화면에서는 청년 참여자 |
| ADMIN | 컨트롤러가 정확히 `ROLE_ADMIN`을 요구 |
| 내부 | 서비스 간 직접 호출용. Gateway 외부 호출로 사용할 수 없음 |

Gateway는 외부 요청의 `X-User-Id`, `X-User-Role`, `X-Internal-*`, `X-Gateway-Secret`을 제거한다. JWT 검증 후 아래 헤더를 직접 생성하므로 클라이언트가 이 값을 신뢰 헤더로 보낼 수 없다.

```text
X-User-Id
X-User-Phone-Number
X-User-Role
X-Gateway-Secret
```

### 1.3 공개 경로

```text
OPTIONS /**
GET  /health
GET  /actuator/**
GET  /test
GET  /api/bank/openbanking/callback
GET  /api/payments/toss/success
GET  /api/payments/toss/fail
POST /api/users
POST /api/users/login
```

그 외 Gateway 경로는 JWT가 필요하다.

### 1.4 멱등성

아래 생성 API는 `Idempotency-Key` 요청 헤더가 필수다.

```text
POST /api/bank/deposits
POST /api/bank/withdrawals
POST /api/payments/toss/charges
POST /api/transfers
```

같은 키와 같은 요청은 기존 결과를 반환한다. 같은 키를 다른 요청 본문에 재사용하면 `IDEMPOTENCY_REQUEST_MISMATCH`가 발생한다.

### 1.5 에러 응답

실제 공통 에러 응답에는 `traceId`가 없고 `timestamp`가 있다.

```json
{
  "code": "INVALID_REQUEST",
  "message": "amount must be an integer from 1 to 10000000",
  "timestamp": "2026-07-06T15:30:00.123"
}
```

| HTTP | 대표 코드 |
| ---: | --- |
| 400 | `INVALID_REQUEST`, `IDEMPOTENCY_KEY_REQUIRED` |
| 401 | `UNAUTHORIZED`, `INVALID_CREDENTIALS` |
| 403 | `FORBIDDEN`, `RESOURCE_OWNER_MISMATCH` |
| 404 | `RESOURCE_NOT_FOUND`, 도메인별 `*_NOT_FOUND` |
| 409 | `USER_ALREADY_EXISTS`, `DUPLICATE_*`, `INVALID_*_STATUS`, `IDEMPOTENCY_REQUEST_MISMATCH` |
| 500 | `INTERNAL_SERVER_ERROR`, `OUTBOX_PUBLISH_FAILED` |
| 502 | `COMPENSATION_REFUND_FAILED` |

## 2. Gateway 라우팅

| 외부 경로 | 대상 서비스 | 내부 경로 |
| --- | --- | --- |
| `/api/users/**` | user-service | `/users/**` |
| `/api/wallets/**` | wallet-service | `/wallets/**` |
| `/api/bank/**` | banking-service | `/bank/**` |
| `/api/payments/**` | banking-service | `/payments/**` |
| `/api/transfers/**` | transfer-service | `/transfers/**` |
| `/api/ledgers/**` | ledger-service | `/ledgers/**` |
| `/api/settlements/**` | settlement-service | `/settlements/**` |
| `/api/families/**` | reward-service | `/families/**` |
| `/api/missions/**` | reward-service | `/missions/**` |
| `/api/cashbook/**` | reward-service | `/cashbook/**` |
| `/test` | banking-service | `/bank/openbanking/callback` |

`/api/auth/**` 라우트는 존재하지 않는다. 회원가입과 로그인은 `/api/users` 하위에 있다.

## 3. User API

### 3.1 엔드포인트

| Method | Path | 인증 | 성공 | 요청 | 응답 |
| --- | --- | --- | ---: | --- | --- |
| POST | `/api/users` | 공개 | 201 | `CreateUserRequest` | `UserResponse` |
| POST | `/api/users/login` | 공개 | 200 | `LoginRequest` | `AuthTokenResponse` |
| GET | `/api/users/me` | JWT | 200 | 없음 | `UserMeResponse` |
| GET | `/api/users/{userId}` | JWT, 본인 | 200 | 없음 | `UserResponse` |
| GET | `/users/internal/{userId}` | 내부 | 200 | 내부 헤더 | `UserResponse` |

### 3.2 회원가입

전화번호는 숫자 10~11자리, 비밀번호는 최소 8자다. `role`은 요청할 수 없다. `inviteCode`가 없거나 서버 설정과 다르면 `CHILD`, 유효한 기관 초대 코드와 일치하면 `PARENT`로 생성된다.

```json
{
  "phoneNumber": "01011112222",
  "password": "password1234",
  "name": "기관 담당자",
  "inviteCode": "{configured-parent-invite-code}"
}
```

회원 생성 시 user-service가 wallet-service에 지갑 생성을 요청한다.

### 3.3 로그인 응답

```json
{
  "accessToken": "jwt",
  "tokenType": "Bearer",
  "expiresIn": 86400000,
  "user": {
    "userId": 1,
    "phoneNumber": "01011112222",
    "name": "기관 담당자",
    "role": "PARENT",
    "status": "ACTIVE"
  }
}
```

`GET /api/users/me`는 위 사용자 필드에 `hasBankAccount`를 추가해 반환한다.

## 4. Wallet API

### 4.1 엔드포인트

| Method | Path | 인증 | 성공 | 요청 | 응답 |
| --- | --- | --- | ---: | --- | --- |
| POST | `/api/wallets` | JWT, 본인 | 201 | `CreateWalletRequest` | `WalletResponse` |
| GET | `/api/wallets/{walletId}` | JWT, 소유자 | 200 | 없음 | `WalletResponse` |
| GET | `/api/wallets/me/transactions` | JWT | 200 | 없음 | `WalletTransactionResponse[]` |
| POST | `/api/wallets/{walletId}/deposit` | JWT, 소유자 또는 내부 | 200 | `WalletBalanceChangeRequest` | `WalletResponse` |
| GET | `/wallets/users/{userId}` | 내부 | 200 | 내부 헤더 | `WalletResponse` |
| POST | `/wallets/{walletId}/withdraw` | 내부 | 200 | `WalletBalanceChangeRequest` | `WalletResponse` |

현재 `GET /api/wallets/me`는 구현되어 있지 않다. 지갑 단건 조회는 `walletId`를 사용하는 경로만 외부에 제공된다.

### 4.2 요청·응답

```json
{
  "userId": 1
}
```

```json
{
  "amount": 5000,
  "referenceType": "MANUAL_CHARGE",
  "referenceId": "manual-charge-001"
}
```

```json
{
  "walletId": 100,
  "userId": 1,
  "balance": 50000,
  "status": "ACTIVE"
}
```

거래 내역은 최근 10건을 반환하며 별도 페이지 파라미터는 없다.

```json
[
  {
    "walletTransactionId": 10,
    "walletId": 100,
    "transactionType": "DEPOSIT",
    "amount": 5000,
    "balanceAfter": 50000,
    "referenceType": "TRANSFER",
    "referenceId": "123",
    "createdAt": "2026-07-06T15:30:00"
  }
]
```

## 5. Banking API

### 5.1 계좌 및 오픈뱅킹

| Method | Path | 인증 | 성공 | 요청 | 응답 |
| --- | --- | --- | ---: | --- | --- |
| POST | `/api/bank/accounts` | JWT | 201 | `CreateBankAccountRequest` | `BankAccountResponse` |
| GET | `/api/bank/accounts` | JWT | 200 | 없음 | `BankAccountResponse[]` |
| GET | `/api/bank/openbanking/authorize-url` | JWT | 200 | 없음 | `OpenBankingAuthorizeUrlResponse` |
| POST | `/api/bank/openbanking/callback` | JWT | 200 | `OpenBankingCallbackRequest` | `OpenBankingCallbackResponse` |
| GET | `/api/bank/openbanking/callback?code=&state=` | 공개 | 302 | query | 프런트엔드 redirect |
| POST | `/api/bank/openbanking/accounts/sync` | JWT | 200 | 없음 | `BankAccountResponse[]` |
| POST | `/api/bank/openbanking/attempts/real-name` | JWT | 200 | `OpenBankingRealNameInquiryRequest` | `OpenBankingAttemptResponse` |
| POST | `/api/bank/openbanking/attempts/receive` | JWT | 200 | `OpenBankingReceiveInquiryRequest` | `OpenBankingAttemptResponse` |
| POST | `/api/bank/openbanking/attempts/deposit-transfer` | JWT | 200 | `OpenBankingDepositTransferRequest` | `OpenBankingAttemptResponse` |
| GET | `/bank/internal/has-account` | 내부 | 200 | `X-User-Id` | `{ "hasBankAccount": true }` |

`GET /test`도 공개 콜백 별칭이며 동일한 banking callback으로 전달된다.

수동 계좌 등록 요청:

```json
{
  "bankCode": "004",
  "accountNumber": "123456789012",
  "accountHolderName": "홍길동",
  "fintechUseNum": null,
  "userSeqNo": null,
  "bankName": "KB국민은행",
  "inquiryAgreeYn": null,
  "transferAgreeYn": null
}
```

OAuth 콜백 요청:

```json
{
  "code": "authorization-code",
  "state": "state-from-authorize-url"
}
```

### 5.2 계좌 입출금

| Method | Path | 인증 | 성공 | 요청 | 응답 |
| --- | --- | --- | ---: | --- | --- |
| POST | `/api/bank/deposits` | JWT + 멱등키 | 201 | `CreateDepositRequest` | `BankingTransferResponse` |
| POST | `/api/bank/withdrawals` | JWT + 멱등키 | 201 | `CreateWithdrawalRequest` | `BankingTransferResponse` |
| GET | `/api/bank/transfers/{bankingTransferId}` | JWT, 소유자 | 200 | 없음 | `BankingTransferResponse` |
| POST | `/api/bank/transfers/{bankingTransferId}/result-check` | JWT, 소유자 | 200 | 없음 | `BankingTransferResponse` |
| POST | `/api/bank/transfers/{bankingTransferId}/compensate` | JWT, 소유자 | 200 | 없음 | `BankingTransferResponse` |

입금과 출금 요청은 같은 형태다.

```json
{
  "bankAccountId": 1,
  "amount": 50000
}
```

`BankingTransferResponse` 필드:

```text
bankingTransferId, transferType, bankAccountId, walletId, amount, status,
walletTransactionId, failureReason, compensationRetryCount,
compensationFailureReason, compensatedAt
```

## 6. Toss Payments API

### 6.1 사용자 결제

| Method | Path | 인증 | 성공 | 요청 | 응답 |
| --- | --- | --- | ---: | --- | --- |
| POST | `/api/payments/toss/charges` | JWT + 멱등키 | 201 | `CreateTossChargeRequest` | `TossChargeCreateResponse` |
| GET | `/api/payments/toss/charges/{chargeId}` | JWT, 소유자 | 200 | 없음 | `TossChargeResponse` |
| POST | `/api/payments/toss/confirm` | JWT | 200 | `TossConfirmRequest` | `TossChargeResponse` |
| GET | `/api/payments/toss/payments/{paymentKey}` | JWT, 소유자 | 200 | 없음 | `TossChargeResponse` |
| POST | `/api/payments/toss/payments/{paymentKey}/cancel` | JWT, 소유자 | 200 | `TossCancelRequest` | `TossChargeResponse` |
| POST | `/api/payments/toss/charges/{chargeId}/compensate` | JWT, 소유자 | 200 | 없음 | `TossChargeResponse` |
| POST | `/api/payments/toss/charges/{chargeId}/ledger-compensate` | JWT, 소유자 | 200 | 없음 | `TossChargeResponse` |

주문 생성:

```json
{
  "amount": 50000,
  "orderName": "PayFlow 지원금 충전"
}
```

결제 승인:

```json
{
  "paymentKey": "toss-payment-key",
  "orderId": "payflow-order-id",
  "amount": 50000
}
```

결제 취소:

```json
{
  "cancelReason": "사용자 요청",
  "cancelAmount": 10000
}
```

`cancelAmount`를 생략하면 전액 취소다.

### 6.2 Toss 콜백 및 웹훅

| Method | Path | 인증 | 성공 | 입력 | 결과 |
| --- | --- | --- | ---: | --- | --- |
| GET | `/api/payments/toss/success?paymentKey=&orderId=&amount=` | 공개 | 302 | query | 결제 확정 후 프런트엔드 redirect |
| GET | `/api/payments/toss/fail?orderId=&code=&message=` | 공개 | 302 | query | 실패 반영 후 프런트엔드 redirect |
| POST | `/api/payments/toss/webhook` | 현재 Gateway JWT 필요 | 200 | `TossPaymentWebhookRequest`, `X-Toss-Signature` | `TossWebhookResponse` |

웹훅 본문:

```json
{
  "eventType": "PAYMENT_STATUS_CHANGED",
  "paymentKey": "toss-payment-key",
  "orderId": "payflow-order-id",
  "transactionKey": "transaction-key",
  "status": "DONE",
  "payload": {}
}
```

응답은 `{ "received": true, "duplicate": false }` 형식이다. 웹훅 secret이 설정된 경우 `X-Toss-Signature`가 필수다.

### 6.3 Toss 운영 API

| Method | Path | 인증 | 성공 | 응답 |
| --- | --- | --- | ---: | --- |
| GET | `/api/payments/toss/operations/summary` | ADMIN | 200 | `TossOperationalSummaryResponse` |
| GET | `/api/payments/toss/operations/compensations` | ADMIN | 200 | `TossChargeSummaryResponse[]` |
| GET | `/api/payments/toss/operations/ledger-compensations` | ADMIN | 200 | `TossChargeSummaryResponse[]` |

운영 요약 필드:

```text
readyCount, completedCount, failedCount, canceledCount,
compensationRequiredCount, ledgerCompensationRequiredCount
```

## 7. Transfer API

### 7.1 사용자 API

| Method | Path | 인증 | 성공 | 요청 | 응답 |
| --- | --- | --- | ---: | --- | --- |
| POST | `/api/transfers` | JWT + 멱등키 | 201 | `CreateTransferRequest` | `TransferResponse` |
| GET | `/api/transfers/{transferId}` | JWT, 참여자 | 200 | 없음 | `TransferResponse` |
| GET | `/api/transfers/by-idempotency-key` | JWT, 참여자 + 멱등키 | 200 | 없음 | `TransferResponse` |
| GET | `/api/transfers?page=0&size=20&sort=` | JWT | 200 | page query | `Page<TransferResponse>` |
| GET | `/api/transfers/outbox/summary` | JWT | 200 | 없음 | `OutboxSummaryResponse` |

송금 요청에는 `memo` 필드가 없다.

```json
{
  "receiverUserId": 2,
  "amount": 5000
}
```

송금액은 정수 `1`~`10,000,000`이며 발신자와 수신자는 달라야 한다.

```json
{
  "transferId": 123,
  "senderUserId": 1,
  "receiverUserId": 2,
  "amount": 5000,
  "status": "SUCCEEDED",
  "failureReason": null,
  "compensationRetryCount": 0,
  "compensationFailureReason": null,
  "compensatedAt": null,
  "createdAt": "2026-07-06T15:30:00"
}
```

페이지 응답은 Spring Data 형식으로 `content`, `totalElements`, `totalPages`, `number`, `size`, `first`, `last` 등을 포함한다.

Outbox 요약 필드:

```text
totalCount, statusCounts[{status,count}], retryableFailureCount,
retryExhaustedCount, oldestPendingEventAgeSeconds, oldestPendingEventCreatedAt
```

### 7.2 송금 보상 API

아래 경로는 controller가 `X-Internal-Secret`을 직접 검증하므로 서비스 간 직접 호출용이다.

| Method | 서비스 내부 Path | 인증 | 응답 |
| --- | --- | --- | --- |
| GET | `/transfers/compensations` | 내부 secret | `TransferResponse[]` |
| GET | `/transfers/compensations/{transferId}` | 내부 secret | `TransferResponse` |
| POST | `/transfers/compensations/{transferId}/refund` | 내부 secret | `TransferResponse` |

Gateway의 `/api/transfers/compensations/**`로 호출하면 외부 `X-Internal-Secret`이 제거되므로 현재 403을 반환한다.

## 8. Policy Mission API

내부 구현 명칭은 `/families`, `/missions`, `/cashbook`, `PARENT`, `CHILD`를 유지한다. 화면 문맥에서는 기관-청년 연결, 정책 미션, 지원금 내역으로 해석한다.

### 8.1 기관-참여자 연결

| Method | Path | 인증 | 성공 | 요청 | 응답 |
| --- | --- | --- | ---: | --- | --- |
| POST | `/api/families/links` | PARENT | 201 | `CreateFamilyLinkRequest` | `FamilyLinkResponse` |
| GET | `/api/families/children` | PARENT | 200 | 없음 | `FamilyLinkResponse[]` |
| GET | `/api/families/parents` | CHILD | 200 | 없음 | `FamilyLinkResponse[]` |

```json
{
  "childUserId": 2
}
```

`FamilyLinkResponse` 필드:

```text
familyLinkId, parentUserId, childUserId, status, childName, childPhoneNumber
```

### 8.2 미션

| Method | Path | 인증 | 성공 | 요청/Query | 응답 |
| --- | --- | --- | ---: | --- | --- |
| POST | `/api/missions` | PARENT | 201 | `CreateMissionRequest` | `MissionResponse` |
| GET | `/api/missions?status=&date=` | PARENT/CHILD | 200 | 선택 query | `MissionResponse[]` |
| GET | `/api/missions/{missionId}` | 연결된 PARENT/CHILD | 200 | 없음 | `MissionResponse` |
| PATCH | `/api/missions/{missionId}/submit` | CHILD | 200 | `SubmitMissionRequest` 또는 body 생략 | `MissionResponse` |
| PATCH | `/api/missions/{missionId}/approve` | PARENT | 200 | 없음 | `MissionResponse` |
| PATCH | `/api/missions/{missionId}/reject` | PARENT | 200 | `RejectMissionRequest` | `MissionResponse` |
| POST | `/api/missions/{missionId}/pay` | PARENT | 200 | 없음 | `MissionResponse` |

```json
{
  "childUserId": 2,
  "title": "청년 금융 교육 참여",
  "description": "수료 화면 제출",
  "rewardAmount": 5000,
  "missionDate": "2026-07-06"
}
```

`evidenceRequired` 필드는 구현되어 있지 않다. `rewardAmount`는 정수 `1`~`10,000,000`이다.

제출과 반려 요청:

```json
{ "submissionNote": "교육 수료 화면을 제출합니다." }
```

```json
{ "reason": "증빙 내용이 부족합니다." }
```

지원금 지급은 내부적으로 `reward-payment-{missionId}` 멱등키로 transfer-service를 호출한다.

`MissionResponse` 필드:

```text
missionId, parentUserId, childUserId, childName, title, description,
rewardAmount, status, missionDate, submissionNote, rejectReason,
transferId, failureReason
```

### 8.3 지원금 내역

| Method | Path | 인증 | 성공 | 응답 |
| --- | --- | --- | ---: | --- |
| GET | `/api/cashbook/parent/summary` | PARENT | 200 | `ParentCreditSummaryResponse` |
| GET | `/api/cashbook/children/{childUserId}/summary` | 본인 CHILD 또는 연결된 PARENT | 200 | `CashbookSummaryResponse` |
| GET | `/api/cashbook/children/{childUserId}/entries` | 본인 CHILD 또는 연결된 PARENT | 200 | `MissionResponse[]` |

```text
ParentCreditSummaryResponse: walletId, creditBalance, monthlyRewardPaid, pendingApprovalCount
CashbookSummaryResponse: childUserId, walletId, walletBalance, paidRewardAmount, paidMissionCount
```

## 9. Ledger API

### 9.1 사용자 조회

| Method | Path | 인증 | 성공 | 응답 |
| --- | --- | --- | ---: | --- |
| GET | `/api/ledgers/entries` | JWT | 200 | 참여 원장 `LedgerEntryResponse[]` |
| GET | `/api/ledgers/entries/{entryId}` | JWT, 참여자 | 200 | `LedgerEntryResponse` |
| GET | `/api/ledgers/transfer-failures` | JWT | 200 | 참여 송금 실패 `TransferFailureEventResponse[]` |
| GET | `/api/ledgers/transfer-failures/{transferId}` | JWT, 참여자 | 200 | `TransferFailureEventResponse` |

`LedgerEntryResponse` 필드:

```text
id, transferId, sourceType, sourceId, entryType, senderUserId,
receiverUserId, amount, createdAt,
lines[{id,userId,accountCode,type,amount}]
```

### 9.2 결제 원장 내부 API

| Method | 서비스 내부 Path | 요청 | 응답 |
| --- | --- | --- | --- |
| POST | `/ledgers/internal/payment-charge` | `PaymentLedgerRequest` | `LedgerEntryResponse` |
| GET | `/ledgers/internal/payment-entry?sourceType=&sourceId=` | query | `LedgerEntryResponse` 또는 404 |

```json
{
  "sourceType": "TOSS_CHARGE",
  "sourceId": 10,
  "entryType": "USER_WALLET_TOPUP",
  "userId": 1,
  "amount": 50000
}
```

## 10. Settlement API

| Method | Path | 인증 | 성공 | 응답 |
| --- | --- | --- | ---: | --- |
| POST | `/api/settlements/daily/{businessDate}` | JWT | 200 | `SettlementRunResponse` |
| GET | `/api/settlements/daily/{businessDate}` | JWT | 200/404 | `SettlementRunResponse` |

`businessDate`는 `yyyy-MM-dd`다. 이미 `COMPLETED` 또는 `WITH_DISCREPANCY`인 실행이 있으면 새 실행을 만들지 않고 기존 결과를 반환한다.

```json
{
  "id": 1,
  "businessDate": "2026-07-05",
  "status": "COMPLETED",
  "transactionCount": 2,
  "discrepancyCount": 0,
  "grossAmount": 10000,
  "cancelAmount": 1000,
  "feeAmount": 270,
  "expectedNetAmount": 8730,
  "completedAt": "2026-07-06T01:00:03"
}
```

```text
grossAmount       = CHARGE 합계
cancelAmount      = CANCEL 합계
feeAmount         = grossAmount × feeRate (기본 0.027, 원 단위 HALF_UP)
expectedNetAmount = grossAmount - cancelAmount - feeAmount
```

## 11. DTO 필드 참조

### 11.1 주요 응답

| DTO | 필드 |
| --- | --- |
| `UserResponse` | `userId`, `phoneNumber`, `name`, `role`, `status` |
| `UserMeResponse` | UserResponse 필드 + `hasBankAccount` |
| `WalletResponse` | `walletId`, `userId`, `balance`, `status` |
| `BankAccountResponse` | `bankAccountId`, `bankCode`, `accountNumberMasked`, `accountHolderName`, `bankName`, `inquiryAgreeYn`, `transferAgreeYn`, `providerCode`, `accountAlias`, `status` |
| `TossChargeCreateResponse` | `chargeId`, `providerCode`, `orderId`, `orderName`, `amount`, `currency`, `status`, `customerKey` |
| `TossChargeResponse` | `chargeId`, `providerCode`, `orderId`, `paymentKey`, `amount`, `status`, `tossStatus`, `walletId`, `walletTransactionId`, 실패·보상·원장 필드, `receiptUrl` |
| `TransferFailureEventResponse` | `transferId`, `senderUserId`, `receiverUserId`, `amount`, `status`, `failureReason`, `createdAt` |

### 11.2 Open Banking 시험 요청

| DTO | 필드 |
| --- | --- |
| `OpenBankingRealNameInquiryRequest` | `bankTranId`, `bankCodeStd`, `accountNum`, `accountHolderInfoType`, `accountHolderInfo`, `tranDtime` |
| `OpenBankingReceiveInquiryRequest` | `bankTranId`, `cntrAccountType`, `cntrAccountNum`, `bankCodeStd`, `accountNum`, `accountHolderName`, `printContent`, `reqClientName`, `reqClientNum`, `reqClientFintechUseNum`, `transferPurpose`, `tranAmt`, `cmsNum`, `tranDtime` |
| `OpenBankingDepositTransferRequest` | `cntrAccountType`, `cntrAccountNum`, `wdPassPhrase`, `wdPrintContent`, `nameCheckOption`, `tranDtime`, `bankTranId`, `fintechUseNum`, `printContent`, `tranAmt`, `reqClientName`, `reqClientFintechUseNum`, `reqClientNum`, `transferPurpose`, `cmsNum`, `withdrawBankTranId` |

시험 요청 응답은 `{ "apiName": "...", "attempted": true }` 형식이다.

## 12. 주요 상태값

| 도메인 | 상태 |
| --- | --- |
| User | `ACTIVE`, `LOCKED`, `WITHDRAWN` |
| Wallet | `ACTIVE`, `LOCKED`, `CLOSED` |
| Banking transfer | `REQUESTED`, `WALLET_WITHDRAWING`, `BANK_PROCESSING`, `BANK_SUCCEEDED`, `WALLET_REFLECTING`, `COMPLETED`, `SUCCEEDED`, `UNKNOWN`, `COMPENSATION_REQUIRED`, `COMPENSATED`, `FAILED` |
| Toss charge | `READY`, `PAYMENT_PENDING`, `PAYMENT_APPROVED`, `WALLET_REFLECTING`, `COMPLETED`, `FAILED`, `CANCELED`, `PARTIAL_CANCELED`, `EXPIRED`, `UNKNOWN`, `COMPENSATION_REQUIRED` |
| Transfer | `REQUESTED`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `COMPENSATION_REQUIRED`, `COMPENSATED` |
| Mission | `CREATED`, `SUBMITTED`, `APPROVED`, `PAID`, `REJECTED`, `CANCELED` |
| Ledger line | `DEBIT`, `CREDIT` |
| Settlement run | `RUNNING`, `COMPLETED`, `WITH_DISCREPANCY`, `FAILED` |
| Reconciliation | `MATCHED`, `MISSING_LEDGER`, `AMOUNT_MISMATCH` |

## 13. 내부 호출 규칙

서비스 간 직접 호출은 서비스 기본 URL과 아래 헤더를 사용한다.

```text
X-Internal-Request: true   # 해당 controller가 요구하는 경우
X-Internal-Secret: {INTERNAL_SERVICE_SECRET}
```

| 내부 API | 추가 요구사항 |
| --- | --- |
| `/users/internal/{userId}` | `X-Internal-Request: true`; secret이 설정된 환경에서는 secret 일치 |
| `/wallets/users/{userId}` | `X-Internal-Request: true` + secret |
| `/wallets/{walletId}/withdraw` | `X-Internal-Request: true` + secret |
| `/wallets/{walletId}/deposit` | 외부 소유자 또는 내부 요청 모두 지원 |
| `/bank/internal/has-account` | `X-Internal-Request: true`; secret 설정 시 일치 |
| `/transfers/compensations/**` | secret 직접 검증 |
| `/ledgers/internal/**` | GatewayRequestFilter의 trusted secret만 적용 |

## 14. 구현 점검 결과 및 주의사항

- 기존 문서의 `GET /api/wallets/me`는 실제 controller에 없어 제거했다.
- 기존 송금 예시의 `memo`와 미션 예시의 `evidenceRequired`는 실제 DTO에 없어 제거했다.
- 로그인 응답에 실제 존재하는 중첩 `user` 필드를 추가했다.
- 에러 응답을 실제 `code`, `message`, `timestamp` 구조로 수정했다.
- 누락됐던 사용자 단건, 지갑 생성/입금, 뱅킹 이체 조회·보상, Toss 조회·취소·콜백, 송금 목록·멱등키 조회, Outbox, 원장, 내부 API를 반영했다.
- Toss 운영 API는 `ROLE_ADMIN`을 요구하지만 현재 가입 가능한 역할 enum은 `PARENT`, `CHILD`뿐이다. 별도 관리자 JWT 발급 경로가 없어 일반 로그인 토큰으로는 호출할 수 없다.
- Toss webhook은 Gateway 공개 경로가 아니므로 현재 `/api/payments/toss/webhook` 호출에 JWT가 필요하다. 실제 Toss 서버 콜백을 받으려면 Gateway 공개 예외와 하위 서비스 신뢰 경계를 별도로 조정해야 한다.
- `/api/ledgers/internal/**`는 이름과 달리 controller 수준의 internal-secret 검증이 없다. 현재 Gateway 라우트에도 포함되어 인증된 외부 사용자가 접근할 수 있으므로 운영 전 경로 차단 또는 secret 검증이 필요하다.
- 정산 실행 API는 JWT만 요구하고 관리자 역할을 검증하지 않는다. 운영 전 권한 분리가 필요하다.
- 송금 보상 API는 Gateway가 외부 internal secret을 제거하는 현재 구조상 서비스 직접 호출로만 사용할 수 있다.
