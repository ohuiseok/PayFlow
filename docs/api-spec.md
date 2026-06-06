# PayFlow API 명세서

이 문서는 현재 코드 기준으로 구현된 API를 정리한 명세입니다.

현재 구현된 API와 `sample-react` 목업 화면 기준으로 구현할 API를 함께 정리합니다.

- 기준 경로: `http://localhost:8080`
- 외부 클라이언트는 `api-gateway`를 통해 `/api/**` 경로로 호출합니다.
- `api-gateway`는 인증된 요청에 대해 내부 서비스로 `X-User-Id`, `X-User-Email`, `X-User-Role` 헤더를 주입합니다.
- 클라이언트가 보낸 `X-User-*`, `X-Internal-*` 헤더는 Gateway에서 제거됩니다.

## 공통

### 인증

공개 API:

- `POST /api/users`
- `POST /api/users/login`
- `/actuator/**`

그 외 API는 아래 헤더가 필요합니다.

```http
Authorization: Bearer {accessToken}
```

### 공통 오류 응답

```json
{
  "code": "UNAUTHORIZED",
  "message": "인증이 필요합니다.",
  "timestamp": "2026-06-05T21:00:00"
}
```

주요 오류 코드:

| HTTP | code | 설명 |
|---:|---|---|
| 400 | INVALID_REQUEST | 요청 값이 올바르지 않음 |
| 401 | UNAUTHORIZED | 인증 필요 |
| 401 | INVALID_CREDENTIALS | 이메일 또는 비밀번호 오류 |
| 403 | FORBIDDEN | 접근 권한 없음 |
| 403 | RESOURCE_OWNER_MISMATCH | 리소스 소유자 불일치 |
| 404 | RESOURCE_NOT_FOUND | 리소스 없음 |
| 404 | WALLET_NOT_FOUND | 지갑 없음 |
| 409 | USER_ALREADY_EXISTS | 이미 가입된 사용자 |
| 409 | DUPLICATE_WALLET | 이미 생성된 지갑 |
| 409 | DUPLICATE_WALLET_REFERENCE | 이미 처리된 지갑 거래 참조 |
| 409 | INSUFFICIENT_BALANCE | 잔액 부족 |
| 500 | INTERNAL_SERVER_ERROR | 서버 내부 오류 |

## User API

Gateway 라우트:

```text
/api/users/** -> user-service /users/**
```

### 회원가입

```http
POST /api/users
Content-Type: application/json
```

요청:

```json
{
  "email": "parent@example.com",
  "password": "password123",
  "name": "지훈"
}
```

요청 필드:

| 필드 | 타입 | 필수 | 제약 |
|---|---|---:|---|
| email | string | O | 이메일 형식 |
| password | string | O | 8자 이상 |
| name | string | O | 빈 값 불가 |

응답: `201 Created`

```json
{
  "userId": 1,
  "email": "parent@example.com",
  "name": "지훈",
  "status": "ACTIVE"
}
```

### 로그인

```http
POST /api/users/login
Content-Type: application/json
```

요청:

```json
{
  "email": "parent@example.com",
  "password": "password123"
}
```

응답: `200 OK`

```json
{
  "accessToken": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "expiresIn": 86400000
}
```

### 사용자 조회

```http
GET /api/users/{userId}
Authorization: Bearer {accessToken}
```

응답: `200 OK`

```json
{
  "userId": 1,
  "email": "parent@example.com",
  "name": "지훈",
  "status": "ACTIVE"
}
```

제약:

- `{userId}`는 인증된 사용자 ID와 같아야 합니다.
- 다르면 `RESOURCE_OWNER_MISMATCH`가 반환됩니다.

## Wallet API

Gateway 라우트:

```text
/api/wallets/** -> wallet-service /wallets/**
```

### 지갑 생성

```http
POST /api/wallets
Authorization: Bearer {accessToken}
Content-Type: application/json
```

요청:

```json
{
  "userId": 1
}
```

응답: `201 Created`

```json
{
  "walletId": 100,
  "userId": 1,
  "balance": 0,
  "status": "ACTIVE"
}
```

제약:

- 요청의 `userId`는 인증된 사용자 ID와 같아야 합니다.
- 사용자당 지갑은 하나만 생성할 수 있습니다.

### 지갑 조회

```http
GET /api/wallets/{walletId}
Authorization: Bearer {accessToken}
```

응답: `200 OK`

```json
{
  "walletId": 100,
  "userId": 1,
  "balance": 85000,
  "status": "ACTIVE"
}
```

제약:

- 지갑 소유자만 조회할 수 있습니다.

### 내부 지갑 조회

내부 서비스 간 직접 호출용 API입니다. Gateway 경유 외부 호출 대상이 아닙니다.
`transfer-service`는 송금 전 sender/receiver 지갑의 존재, 소유자, 상태를 확인할 때 이 API를 사용합니다.

```http
GET /internal/wallets/{walletId}
X-Internal-Request: true
X-Internal-Secret: {INTERNAL_SERVICE_SECRET}
```

응답: `200 OK`

```json
{
  "walletId": 100,
  "userId": 1,
  "balance": 85000,
  "status": "ACTIVE"
}
```

제약:

- 내부 요청만 허용됩니다.
- 외부에서 Gateway를 통해 호출할 수 없습니다.
- `X-Internal-Secret`이 없거나 다르면 `FORBIDDEN`이 반환됩니다.

### 지갑 입금

```http
POST /api/wallets/{walletId}/deposit
Authorization: Bearer {accessToken}
Content-Type: application/json
```

요청:

```json
{
  "amount": 50000,
  "referenceType": "MANUAL_CHARGE",
  "referenceId": "charge-20260605-0001"
}
```

응답: `200 OK`

```json
{
  "walletId": 100,
  "userId": 1,
  "balance": 135000,
  "status": "ACTIVE"
}
```

제약:

- 외부 사용자 요청이면 지갑 소유자만 입금할 수 있습니다.
- `amount`는 1원 이상 10,000,000원 이하의 정수여야 합니다.
- 같은 `walletId`, 거래 유형, `referenceType`, `referenceId` 조합은 중복 처리되지 않습니다.
- 같은 참조로 같은 금액을 다시 요청하면 기존 처리 결과를 반환합니다.
- 같은 참조로 다른 금액을 요청하면 `DUPLICATE_WALLET_REFERENCE`가 반환됩니다.

내부 요청:

```http
X-Internal-Request: true
X-Internal-Secret: {INTERNAL_SERVICE_SECRET}
```

Gateway는 외부 요청의 `X-Internal-*` 헤더를 제거하므로 내부 서비스 간 직접 호출에서만 사용합니다.

### 지갑 출금

내부 서비스 간 직접 호출용 API입니다. Gateway 경유 외부 호출 대상이 아닙니다.

```http
POST /wallets/{walletId}/withdraw
Content-Type: application/json
X-Internal-Request: true
X-Internal-Secret: {INTERNAL_SERVICE_SECRET}
```

요청:

```json
{
  "amount": 3000,
  "referenceType": "TRANSFER",
  "referenceId": "transfer-5000"
}
```

응답: `200 OK`

```json
{
  "walletId": 100,
  "userId": 1,
  "balance": 82000,
  "status": "ACTIVE"
}
```

제약:

- 출금은 내부 요청만 허용됩니다.
- 외부에서 Gateway를 통해 `/api/wallets/{walletId}/withdraw`를 호출하면 `X-Internal-*` 헤더가 제거되어 `FORBIDDEN`이 반환됩니다.
- 잔액이 부족하면 `INSUFFICIENT_BALANCE`가 반환됩니다.
- `amount`와 참조 중복 처리 규칙은 입금과 같습니다.

## Enum

### UserStatus

| 값 | 설명 |
|---|---|
| ACTIVE | 활성 |
| LOCKED | 잠김 |
| WITHDRAWN | 탈퇴 |

### WalletStatus

| 값 | 설명 |
|---|---|
| ACTIVE | 활성 |
| LOCKED | 잠김 |
| CLOSED | 종료 |

### WalletReferenceType

| 값 | 설명 |
|---|---|
| MANUAL_CHARGE | 수동 충전 |
| TRANSFER | 송금/보상 지급 |
| OPEN_BANKING_CHARGE | 오픈뱅킹 충전 |
| OPEN_BANKING_WITHDRAWAL | 오픈뱅킹 출금 |
| OPEN_BANKING_REFUND | 오픈뱅킹 환불 |

## Gateway 라우트 현황

아래 라우트는 Gateway에 등록되어 있거나 MVP에서 우선 등록할 라우트입니다.

| 외부 경로 | 대상 서비스 | 현재 API 구현 상태 |
|---|---|---|
| `/api/users/**` | user-service | 구현됨 |
| `/api/wallets/**` | wallet-service | 구현됨 |
| `/api/credits/**` | banking-service | MVP 공개 충전 API |
| `/api/bank/**` | banking-service | 전환/내부 테스트용 legacy 경로 |
| `/api/transfers/**` | transfer-service | 컨트롤러 미구현 |
| `/api/ledgers/**` | ledger-service | 컨트롤러 미구현 |
| `/api/settlements/**` | settlement-service | 보강/2차 |

추가 필요 라우트:

| 외부 경로 | 권장 서비스 | 용도 |
|---|---|---|
| `/api/families/**` | family/reward-service | 부모-자녀 연결 |
| `/api/missions/**` | reward-service | 미션 등록, 제출, 승인, 반려 |
| `/api/cashbook/**` | reward/wallet-service | 자녀 캐시북 |
| `/api/notifications/**` | notification/reward-service | 보강/2차 알림 |
| `/api/settings/**` | user-service | 보강/2차 프로필/설정 |
| `/api/files/**` | file/reward-service | 보강/2차 미션 인증 사진 업로드 URL |

## 목업 화면 기준 구현 예정 API

아래 API는 `sample-react/assets/mockups/screens` 화면 흐름을 구현하기 위한 명세입니다.

### 화면과 API 매핑

| 화면 | 필요한 API |
|---|---|
| `01-login.svg` | 로그인, 회원가입 |
| `10-signup-role.svg` | 역할 포함 회원가입 |
| `11-family-link.svg` | 부모 초대 코드 생성, 가족 연결 요청 승인/거절 |
| `17-child-invite-code.svg` | 자녀 초대 코드 입력, 연결 요청 생성 |
| `18-family-connected.svg` | 가족 연결 상태 조회 |
| `02-parent-home.svg` | 부모 홈 요약, 진행 중 미션 목록 |
| `03-credit-charge.svg` | 크레딧 충전 요청 |
| `14-charge-result.svg` | 충전 결과 조회 |
| `04-mission-create.svg` | 미션 등록 |
| `05-child-home.svg` | 자녀 홈 요약, 자녀 미션 목록 |
| `19-mission-calendar.svg` | 월별 날짜 기반 미션 캘린더 |
| `12-mission-detail-status.svg` | 미션 상세, 상태 흐름 조회 |
| `06-mission-submit.svg` | 미션 완료 제출 |
| `07-parent-approval.svg` | 부모 승인/반려 |
| `13-reject-resubmit.svg` | 반려 사유 조회, 재제출 |
| `08-cashbook.svg` | 자녀 캐시북 |
| `09-parent-history.svg` | 보강/2차: 부모 지급/정산 내역 |
| `15-notifications.svg` | 보강/2차: 알림 목록 |
| `16-settings-profile.svg` | 보강/2차: 프로필, 가족, 설정 |

추가로 화면에 직접 크게 드러나지는 않지만 구현에 필요한 API:

- 충전 계좌 목록/등록/삭제
- 미션 수정/취소

보강/2차 API:

- 미션 인증 사진 업로드 URL 발급
- 자녀 캐시북 지출 기록
- 알림 안 읽은 개수, 전체 읽음 처리
- 프로필 수정, 알림 설정 변경
- 가족 연결 해제

## User 확장 API, 보강/2차

### 역할 포함 회원가입

MVP에서는 기본 회원가입/로그인/JWT/사용자 조회를 우선합니다.
역할 기반 화면 진입이 필요해지는 보강/2차에서 회원가입 API에 `role` 필드를 추가합니다.

```http
POST /api/users
Content-Type: application/json
```

요청:

```json
{
  "email": "parent@example.com",
  "password": "password123",
  "name": "지훈",
  "role": "PARENT"
}
```

응답:

```json
{
  "userId": 1,
  "email": "parent@example.com",
  "name": "지훈",
  "role": "PARENT",
  "status": "ACTIVE"
}
```

역할:

| 값 | 설명 |
|---|---|
| PARENT | 크레딧 충전, 자녀 연결, 미션 등록, 제출 승인/반려 |
| CHILD | 가족 연결 요청, 미션 조회, 완료 제출, 캐시북 조회 |

## Family API

### 부모 초대 코드 생성

```http
POST /api/families/invitations
Authorization: Bearer {parentAccessToken}
```

응답:

```json
{
  "invitationId": 10,
  "inviteCode": "PF4829",
  "expiresAt": "2026-06-05T22:30:00",
  "status": "ACTIVE"
}
```

### 자녀 초대 코드 확인

```http
GET /api/families/invitations/{inviteCode}
Authorization: Bearer {childAccessToken}
```

응답:

```json
{
  "inviteCode": "PF4829",
  "parentUserId": 1,
  "parentName": "지훈",
  "status": "ACTIVE"
}
```

### 자녀 가족 연결 요청

```http
POST /api/families/link-requests
Authorization: Bearer {childAccessToken}
Content-Type: application/json
```

요청:

```json
{
  "inviteCode": "PF4829"
}
```

응답:

```json
{
  "requestId": 20,
  "parentUserId": 1,
  "childUserId": 2,
  "status": "PENDING"
}
```

### 부모 가족 연결 승인

```http
POST /api/families/link-requests/{requestId}/approve
Authorization: Bearer {parentAccessToken}
```

응답:

```json
{
  "familyId": 100,
  "parentUserId": 1,
  "childUserId": 2,
  "childName": "민준",
  "status": "CONNECTED"
}
```

### 부모 가족 연결 거절

```http
POST /api/families/link-requests/{requestId}/reject
Authorization: Bearer {parentAccessToken}
Content-Type: application/json
```

요청:

```json
{
  "reason": "잘못 보낸 요청입니다."
}
```

응답:

```json
{
  "requestId": 20,
  "status": "REJECTED"
}
```

### 내 가족 목록 조회

```http
GET /api/families/me
Authorization: Bearer {accessToken}
```

응답:

```json
{
  "role": "PARENT",
  "families": [
    {
      "familyId": 100,
      "childUserId": 2,
      "childName": "민준",
      "status": "CONNECTED"
    }
  ]
}
```

## Credit API

### 부모 크레딧 요약

```http
GET /api/credits/parent/summary
Authorization: Bearer {parentAccessToken}
```

응답:

```json
{
  "parentUserId": 1,
  "walletId": 100,
  "creditBalance": 85000,
  "monthlyRewardPaid": 12000,
  "pendingApprovalCount": 1
}
```

### 충전 계좌 목록 조회

```http
GET /api/credits/bank-accounts
Authorization: Bearer {parentAccessToken}
```

응답:

```json
{
  "bankAccounts": [
    {
      "bankAccountId": "bank-account-001",
      "bankName": "국민",
      "maskedAccountNumber": "123-****-7890",
      "primary": true
    }
  ]
}
```

### 충전 계좌 등록

```http
POST /api/credits/bank-accounts
Authorization: Bearer {parentAccessToken}
Content-Type: application/json
```

요청:

```json
{
  "bankName": "국민",
  "accountNumber": "1234567890",
  "accountHolderName": "지훈"
}
```

응답:

```json
{
  "bankAccountId": "bank-account-001",
  "bankName": "국민",
  "maskedAccountNumber": "123-****-7890",
  "primary": true
}
```

### 충전 계좌 삭제

```http
DELETE /api/credits/bank-accounts/{bankAccountId}
Authorization: Bearer {parentAccessToken}
```

응답: `204 No Content`

### 크레딧 충전 요청

```http
POST /api/credits/charges
Authorization: Bearer {parentAccessToken}
Idempotency-Key: 20260605-user1-charge-001
Content-Type: application/json
```

요청:

```json
{
  "amount": 50000,
  "bankAccountId": "bank-account-001"
}
```

응답:

```json
{
  "chargeId": "charge-20260605-0001",
  "amount": 50000,
  "status": "PROCESSING"
}
```

정책:

- `Idempotency-Key`는 필수입니다.
- 같은 key와 같은 body는 기존 충전 요청 결과를 반환합니다.
- 같은 key와 다른 body는 `409 Conflict`를 반환합니다.
- 외부 은행망 성공이 확정되기 전에는 wallet-service 입금을 호출하지 않습니다.

### 크레딧 충전 결과 조회

```http
GET /api/credits/charges/{chargeId}
Authorization: Bearer {parentAccessToken}
```

응답:

```json
{
  "chargeId": "charge-20260605-0001",
  "amount": 50000,
  "status": "COMPLETED",
  "walletId": 100,
  "balanceAfter": 135000
}
```

### 크레딧 출금 요청

운영용 화면 목업에는 아직 없지만, banking-service 구현문서와 결제 핵심 고도화 범위에 포함되는 최소 출금 API입니다.

```http
POST /api/credits/withdrawals
Authorization: Bearer {accessToken}
Idempotency-Key: 20260605-user1-withdrawal-001
Content-Type: application/json
```

요청:

```json
{
  "walletId": 100,
  "bankAccountId": 10,
  "amount": 30000
}
```

응답:

```json
{
  "withdrawalId": "withdrawal-20260605-0001",
  "walletId": 100,
  "bankAccountId": 10,
  "amount": 30000,
  "status": "PROCESSING"
}
```

정책:

- `Idempotency-Key`는 필수입니다.
- 같은 key와 같은 body는 기존 출금 요청 결과를 반환합니다.
- 지갑 차감 성공 후 은행망 입금이체 실패 시 `COMPENSATION_REQUIRED`로 남깁니다.

## Mission API

### 부모 미션 등록

```http
POST /api/missions
Authorization: Bearer {parentAccessToken}
Content-Type: application/json
```

요청:

```json
{
  "childUserId": 2,
  "title": "수학 문제집 3쪽 풀기",
  "description": "오늘 배운 나눗셈까지 풀기",
  "rewardAmount": 3000,
  "missionDate": "2026-06-05",
  "evidenceRequired": true
}
```

응답:

```json
{
  "missionId": 1000,
  "parentUserId": 1,
  "childUserId": 2,
  "title": "수학 문제집 3쪽 풀기",
  "rewardAmount": 3000,
  "status": "REGISTERED",
  "missionDate": "2026-06-05"
}
```

### 미션 수정

미션이 아직 자녀에게 제출되지 않은 상태에서만 수정할 수 있습니다.

```http
PATCH /api/missions/{missionId}
Authorization: Bearer {parentAccessToken}
Content-Type: application/json
```

요청:

```json
{
  "title": "수학 문제집 4쪽 풀기",
  "description": "오늘 배운 나눗셈까지 풀기",
  "rewardAmount": 4000,
  "missionDate": "2026-06-06",
  "evidenceRequired": true
}
```

응답:

```json
{
  "missionId": 1000,
  "title": "수학 문제집 4쪽 풀기",
  "rewardAmount": 4000,
  "status": "REGISTERED",
  "missionDate": "2026-06-06"
}
```

### 미션 취소

```http
POST /api/missions/{missionId}/cancel
Authorization: Bearer {parentAccessToken}
Content-Type: application/json
```

요청:

```json
{
  "reason": "오늘은 미션을 진행하지 않기로 했습니다."
}
```

응답:

```json
{
  "missionId": 1000,
  "status": "CANCELED"
}
```

### 미션 목록 조회

```http
GET /api/missions?role=parent&status=active
Authorization: Bearer {parentAccessToken}
```

```http
GET /api/missions?role=child&status=active
Authorization: Bearer {childAccessToken}
```

응답:

```json
{
  "missions": [
    {
      "missionId": 1000,
      "childName": "민준",
      "title": "수학 문제집 3쪽 풀기",
      "rewardAmount": 3000,
      "status": "REGISTERED",
      "missionDate": "2026-06-05"
    }
  ]
}
```

### 미션 캘린더 조회

부모와 자녀가 월별 날짜 기준으로 미션을 확인하기 위한 API입니다.

```http
GET /api/missions/calendar?year=2026&month=6&role=child
Authorization: Bearer {childAccessToken}
```

부모가 특정 자녀 기준으로 조회할 때:

```http
GET /api/missions/calendar?year=2026&month=6&role=parent&childUserId=2
Authorization: Bearer {parentAccessToken}
```

응답:

```json
{
  "year": 2026,
  "month": 6,
  "summary": {
    "completedMissionCount": 3,
    "totalRewardAmount": 7000,
    "pendingMissionCount": 2
  },
  "days": [
    {
      "date": "2026-06-05",
      "missions": [
        {
          "missionId": 1000,
          "childUserId": 2,
          "childName": "민준",
          "title": "수학 문제집 3쪽 풀기",
          "rewardAmount": 3000,
          "status": "PAID"
        }
      ]
    }
  ]
}
```

### 미션 상세 조회

```http
GET /api/missions/{missionId}
Authorization: Bearer {accessToken}
```

응답:

```json
{
  "missionId": 1000,
  "parentUserId": 1,
  "parentName": "지훈",
  "childUserId": 2,
  "childName": "민준",
  "title": "수학 문제집 3쪽 풀기",
  "description": "오늘 배운 나눗셈까지 풀기",
  "rewardAmount": 3000,
  "status": "SUBMITTED",
  "missionDate": "2026-06-05",
  "submittedAt": "2026-06-05T20:30:00",
  "rejectionReason": null
}
```

### 자녀 미션 완료 제출

```http
POST /api/missions/{missionId}/submit
Authorization: Bearer {childAccessToken}
Content-Type: application/json
```

요청:

```json
{
  "memo": "오늘 배운 나눗셈까지 풀었어요.",
  "evidenceImageUrl": "https://example.com/evidence/mission-1000.jpg"
}
```

응답:

```json
{
  "missionId": 1000,
  "status": "SUBMITTED",
  "submittedAt": "2026-06-05T20:30:00"
}
```

### 부모 미션 승인

```http
POST /api/missions/{missionId}/approve
Authorization: Bearer {parentAccessToken}
```

응답:

```json
{
  "missionId": 1000,
  "missionSubmissionId": 9001,
  "status": "PAID",
  "transferId": 5000,
  "rewardAmount": 3000,
  "paidAt": "2026-06-05T20:40:00"
}
```

처리 방향:

- 승인 API 경로는 `missionId`를 사용하지만, 보상 지급 멱등키는 승인 대상 제출 건의 `missionSubmissionId`로 만든다.
- 예: `missionSubmissionId = 9001`이면 `Idempotency-Key = reward-payment-9001`.
- 이 멱등키는 클라이언트가 생성해 보내는 값이 아니라 reward-service가 내부적으로 생성해 transfer-service에 전달하는 값이다.
- 승인 대상 제출 건은 현재 `SUBMITTED` 상태인 최신 `missionSubmissionId`로 확정한다.

- 부모 지갑에서 `rewardAmount` 출금
- 자녀 지갑에 `rewardAmount` 입금
- 캐시북/원장 기록 생성
- 같은 `Idempotency-Key` 재요청은 같은 결과 반환

### 부모 미션 반려

```http
POST /api/missions/{missionId}/reject
Authorization: Bearer {parentAccessToken}
Content-Type: application/json
```

요청:

```json
{
  "reason": "마지막 페이지 사진이 잘 안 보여."
}
```

응답:

```json
{
  "missionId": 1000,
  "status": "REJECTED",
  "rejectionReason": "마지막 페이지 사진이 잘 안 보여."
}
```

### 자녀 미션 재제출

```http
POST /api/missions/{missionId}/resubmit
Authorization: Bearer {childAccessToken}
Content-Type: application/json
```

요청:

```json
{
  "memo": "사진을 다시 첨부했어요.",
  "evidenceImageUrl": "https://example.com/evidence/mission-1000-v2.jpg"
}
```

응답:

```json
{
  "missionId": 1000,
  "status": "SUBMITTED",
  "submittedAt": "2026-06-05T21:00:00"
}
```

## File API, 보강/2차

미션 완료 인증 사진을 직접 API 서버로 업로드하지 않고, 업로드 URL을 발급받아 사용하는 방식의 초안입니다.

### 미션 인증 사진 업로드 URL 발급

```http
POST /api/files/mission-evidence/upload-url
Authorization: Bearer {childAccessToken}
Content-Type: application/json
```

요청:

```json
{
  "missionId": 1000,
  "fileName": "math-page.jpg",
  "contentType": "image/jpeg"
}
```

응답:

```json
{
  "uploadUrl": "https://example.com/upload/mission-1000",
  "fileUrl": "https://example.com/evidence/mission-1000.jpg",
  "expiresIn": 300
}
```

## Cashbook API

### 자녀 캐시북 요약

```http
GET /api/cashbook/children/{childUserId}/summary
Authorization: Bearer {accessToken}
```

응답:

```json
{
  "childUserId": 2,
  "childName": "민준",
  "walletId": 200,
  "balance": 17000,
  "weeklyEarned": 7000,
  "completedMissionCount": 3
}
```

권한:

- 자녀 본인
- 연결된 부모

### 자녀 캐시북 내역

```http
GET /api/cashbook/children/{childUserId}/entries
Authorization: Bearer {accessToken}
```

응답:

```json
{
  "entries": [
    {
      "entryId": 1,
      "title": "수학 문제집 3쪽",
      "description": "미션 보상",
      "amount": 3000,
      "type": "EARNED",
      "createdAt": "2026-06-05T20:40:00"
    }
  ]
}
```

### 자녀 캐시북 지출 기록

보강/2차 API입니다.
자녀가 보상금을 어디에 썼는지 기록하기 위한 기능이며, 우선은 캐시북 기록만 남깁니다.
실제 wallet 차감은 출금/소비 도메인으로 분리해 별도 정책을 확정합니다.

```http
POST /api/cashbook/children/{childUserId}/entries
Authorization: Bearer {childAccessToken}
Content-Type: application/json
```

요청:

```json
{
  "title": "간식 사기",
  "amount": 1500,
  "description": "편의점 간식",
  "type": "SPENT"
}
```

응답:

```json
{
  "entryId": 2,
  "title": "간식 사기",
  "amount": -1500,
  "type": "SPENT",
  "createdAt": "2026-06-05T21:20:00"
}
```

## Parent History API, 보강/2차

### 부모 지급/정산 내역

```http
GET /api/parent-history/rewards
Authorization: Bearer {parentAccessToken}
```

응답:

```json
{
  "monthlyRewardPaid": 12000,
  "items": [
    {
      "historyId": 1,
      "title": "민준 미션 보상",
      "amount": -3000,
      "status": "PAID",
      "recordedInLedger": true,
      "createdAt": "2026-06-05T20:40:00"
    }
  ],
  "settlement": {
    "status": "PENDING",
    "message": "일별 보상 지급 합계 정산 처리 예정"
  }
}
```

## Notification API, 보강/2차

### 안 읽은 알림 개수 조회

```http
GET /api/notifications/unread-count
Authorization: Bearer {accessToken}
```

응답:

```json
{
  "unreadCount": 3
}
```

### 알림 목록 조회

```http
GET /api/notifications
Authorization: Bearer {accessToken}
```

응답:

```json
{
  "notifications": [
    {
      "notificationId": 1,
      "title": "민준이가 미션을 제출했어요",
      "body": "수학 문제집 3쪽",
      "type": "MISSION_SUBMITTED",
      "read": false,
      "createdAt": "2026-06-05T20:30:00"
    }
  ]
}
```

### 알림 읽음 처리

```http
PATCH /api/notifications/{notificationId}/read
Authorization: Bearer {accessToken}
```

응답:

```json
{
  "notificationId": 1,
  "read": true
}
```

### 모든 알림 읽음 처리

```http
PATCH /api/notifications/read-all
Authorization: Bearer {accessToken}
```

응답:

```json
{
  "updatedCount": 3
}
```

## Settings API, 보강/2차

### 내 프로필 조회

```http
GET /api/settings/profile
Authorization: Bearer {accessToken}
```

응답:

```json
{
  "userId": 1,
  "name": "지훈",
  "email": "parent@example.com",
  "role": "PARENT",
  "familyCount": 2,
  "dummyDataMode": false
}
```

### 내 프로필 수정

```http
PATCH /api/settings/profile
Authorization: Bearer {accessToken}
Content-Type: application/json
```

요청:

```json
{
  "name": "지훈",
  "email": "parent-new@example.com"
}
```

응답:

```json
{
  "userId": 1,
  "name": "지훈",
  "email": "parent-new@example.com",
  "role": "PARENT",
  "status": "ACTIVE"
}
```

### 알림 설정 조회

```http
GET /api/settings/notification-preferences
Authorization: Bearer {accessToken}
```

응답:

```json
{
  "missionSubmitted": true,
  "missionApproved": true,
  "missionRejected": true,
  "chargeCompleted": true
}
```

### 알림 설정 변경

```http
PATCH /api/settings/notification-preferences
Authorization: Bearer {accessToken}
Content-Type: application/json
```

요청:

```json
{
  "missionSubmitted": true,
  "missionApproved": true,
  "missionRejected": true,
  "chargeCompleted": false
}
```

응답:

```json
{
  "missionSubmitted": true,
  "missionApproved": true,
  "missionRejected": true,
  "chargeCompleted": false
}
```

### 가족 연결 해제

```http
DELETE /api/families/{familyId}
Authorization: Bearer {accessToken}
```

응답: `204 No Content`

### 로그아웃

JWT를 서버에서 무효화하지 않는 정책이면 클라이언트에서 토큰만 삭제합니다. 서버 블랙리스트를 사용할 경우 아래 API를 구현합니다.

```http
POST /api/users/logout
Authorization: Bearer {accessToken}
```

응답: `204 No Content`

## 추가 상태 값

백엔드 내부 enum은 영어 값을 사용하고, 프론트 화면에는 한국어 라벨로 변환해서 표시합니다.

### MissionStatus

| 값 | 화면 라벨 | 설명 |
|---|---|---|
| REGISTERED | 등록 완료 | 부모가 미션을 등록함 |
| SUBMITTED | 제출 완료 | 자녀가 완료 인증을 제출함 |
| REJECTED | 반려됨 | 부모가 제출을 반려함 |
| PAID | 지급 완료 | 부모 승인 후 보상 지급 완료 |
| CANCELED | 취소됨 | 부모가 미션을 취소함 |

### FamilyLinkStatus

| 값 | 화면 라벨 | 설명 |
|---|---|---|
| ACTIVE | 사용 가능 | 초대 코드 사용 가능 |
| PENDING | 요청 완료 | 자녀가 연결 요청을 보냄 |
| CONNECTED | 연결 완료 | 부모가 연결 승인 |
| REJECTED | 거절됨 | 부모가 연결 거절 |
| EXPIRED | 만료됨 | 초대 코드 만료 |

### ChargeStatus, 화면 표시용

화면은 아래처럼 단순한 충전 상태를 사용한다.

| 값 | 화면 라벨 | 설명 |
|---|---|---|
| REQUESTED | 요청 완료 | 충전 요청 생성 |
| PROCESSING | 처리 중 | 은행망 또는 wallet 반영 처리 중 |
| COMPLETED | 완료 | 충전 완료 |
| FAILED | 실패 | 충전 실패 |
| UNKNOWN | 확인 필요 | 결과조회 또는 운영자 확인 필요 |
| RETRY_REQUIRED | 재처리 필요 | 은행 성공 후 wallet 반영 재처리 필요 |

### BankingTransferStatus, 서버 enum

서버는 충전/출금의 세부 상태를 `BankingTransferStatus`로 저장한다.

| 값 | 화면 라벨 | 설명 |
|---|---|---|
| REQUESTED | 요청 완료 | 충전/출금 요청 생성 |
| BANK_REQUESTED | 처리 중 | 오픈뱅킹 또는 mock PG 요청 직전/요청 중 |
| BANK_PROCESSING | 처리 중 | 은행망 처리 중 또는 결과조회 대기 |
| BANK_SUCCEEDED | 은행 성공 | 은행 거래 성공 확정, wallet 반영 전 |
| WALLET_REFLECTING | 반영 중 | wallet-service 입금 반영 중 |
| COMPLETED | 완료 | 은행 성공과 wallet 반영 완료 |
| FAILED | 실패 | 은행 거래 실패 또는 wallet 반영 전 실패 |
| UNKNOWN | 확인 필요 | timeout/응답 미수신으로 결과조회 필요 |
| BANK_SUCCESS_BUT_WALLET_FAILED | 재처리 필요 | 은행 성공 후 wallet-service 입금 반영 실패 |

서버 상태와 화면 상태 매핑:

| BankingTransferStatus | ChargeStatus |
|---|---|
| REQUESTED | REQUESTED |
| BANK_REQUESTED | PROCESSING |
| BANK_PROCESSING | PROCESSING |
| BANK_SUCCEEDED | PROCESSING |
| WALLET_REFLECTING | PROCESSING |
| COMPLETED | COMPLETED |
| FAILED | FAILED |
| UNKNOWN | UNKNOWN |
| BANK_SUCCESS_BUT_WALLET_FAILED | RETRY_REQUIRED |

### WithdrawalStatus

| 값 | 화면 라벨 | 설명 |
|---|---|---|
| REQUESTED | 요청 완료 | 출금 요청 생성 |
| PROCESSING | 처리 중 | 지갑 차감 또는 은행망 입금이체 처리 중 |
| COMPLETED | 완료 | 출금 완료 |
| FAILED | 실패 | 출금 실패 |
| COMPENSATION_REQUIRED | 보상 필요 | 지갑 차감 후 외부 입금이체 실패로 보상 처리 필요 |

## 프론트 더미데이터 모드 참고

`sample-react`는 API 없이 화면을 확인할 수 있도록 더미데이터 모드를 제공합니다.

```bash
EXPO_PUBLIC_USE_DUMMY_DATA=true
```

API 연동 모드로 전환하려면:

```bash
EXPO_PUBLIC_USE_DUMMY_DATA=false
EXPO_PUBLIC_API_BASE_URL=http://localhost:8080
```
