# 06. Reward Service

`reward-service`는 PayFlow의 기존 지갑/송금 인프라 위에 얹는 어린이 금융 습관 기능이다.

부모가 실제 보상금이 걸린 일을 등록하고, 아이가 일을 완료하면 부모 승인 후 부모 지갑에서 아이 지갑으로 용돈이 지급된다. 캐시북은 아이가 "무엇을 해서 돈을 벌었는지"를 보여주는 학습 기록 역할을 한다.

핵심 메시지:

```text
아이에게 돈은 충전되는 숫자가 아니라, 내가 만든 가치의 기록이 된다.
```

## 목표

MVP 필수 기능:

```text
부모가 보상 미션을 등록한다.
부모 초대 코드와 가족 연결 요청을 관리한다.
아이가 미션 완료를 요청한다.
부모가 완료 요청을 승인하거나 거절한다.
반려된 미션은 아이가 재제출할 수 있다.
승인되면 부모 지갑에서 아이 지갑으로 실제 용돈이 송금된다.
지급 완료된 미션은 캐시북에 기록된다.
아이와 연결된 부모가 캐시북 요약/내역을 조회한다.
월별 missionDate 기준 미션 캘린더를 조회한다.
```

보강/2차 기능:

```text
부모 지급/정산 내역을 조회한다.
알림 목록과 읽음 처리를 제공한다.
미션 인증 사진 업로드 URL을 발급한다.
아이의 수동 지출 기록을 추가한다.
```

구현하지 않을 기능:

```text
반복 미션 자동 생성
아이 전용 카드 발급
외부 PG/카드사 실결제 연동
교육 콘텐츠 추천
실제 파일 바이너리 저장
Push 알림 외부 발송
```

## 서비스 경계

`reward-service`는 미션과 보상 지급 흐름의 진실만 가진다. 지갑 잔액의 진실은 `wallet-service`, 송금 상태의 진실은 `transfer-service`, 원장 기록의 진실은 `ledger-service`가 가진다.

```text
reward-service
- 보상 미션 등록
- 가족 연결
- 완료 요청
- 부모 승인/거절
- 보상 지급 요청
- 캐시북 조회/기록
- 보강/2차: 알림 조회/읽음 처리
- 보강/2차: 인증 사진 업로드 URL 발급

transfer-service
- 부모 지갑 -> 아이 지갑 송금
- Idempotency-Key 기반 중복 지급 방지
- 송금 상태 관리
- Outbox 이벤트 저장

wallet-service
- 실제 지갑 잔액 차감/증가
- reference 기반 중복 반영 방지

ledger-service
- 보상 지급 송금에 대한 원장 기록
```

`reward-service`가 다른 서비스의 DB를 직접 읽거나 쓰지 않는다.

## 서비스 구성

```text
Client
  |
API Gateway
  |
  +-- user-service
  +-- wallet-service
  +-- transfer-service
  +-- ledger-service
  +-- reward-service
```

초기 구현에서는 `reward-service`가 OpenFeign으로 `transfer-service`를 호출한다.
알림과 파일 업로드 URL은 별도 서비스로 분리하지 않고 reward-service 내부 모듈로 둘 수 있지만, MVP에서는 구현하지 않는다.
보상 지급 성공 이벤트를 별도로 구독하는 구조도 2차 범위로 둔다.

## 핵심 흐름

### 1. 가족 연결

```text
1. 부모가 초대 코드를 생성한다.
2. 아이가 초대 코드를 입력하고 연결 요청을 생성한다.
3. 부모가 연결 요청을 승인하거나 거절한다.
4. 승인되면 Family 관계가 CONNECTED가 된다.
```

가족 연결 API:

```text
POST /families/invitations
GET /families/invitations/{inviteCode}
POST /families/link-requests
POST /families/link-requests/{requestId}/approve
POST /families/link-requests/{requestId}/reject
GET /families/me
DELETE /families/{familyId}
```

### 2. 부모 미션 등록

```text
1. 부모가 아이, 보상금, 미션 수행 날짜, 제목을 입력한다.
2. reward-service가 가족 연결 여부를 확인한다.
3. reward-service가 부모/아이 식별자와 지갑 ID를 저장한다.
3. 상태는 REGISTERED가 된다.
```

초기 구현에서는 요청의 `parentUserId`는 Gateway의 `X-User-Id`를 신뢰한다. `childUserId`, `parentWalletId`, `childWalletId`는 요청으로 받되, Family 관계로 부모/아이 연결 여부를 검증한다.

### 3. 아이 완료 요청

```text
1. 아이가 미션을 완료했다고 요청한다.
2. 필요한 경우 인증 사진 업로드 URL을 먼저 발급받는다.
3. reward-service가 미션의 childUserId와 요청 사용자 ID를 비교한다.
4. 제출 메모와 evidenceImageUrl을 저장한다.
5. 상태를 SUBMITTED로 변경한다.
6. submittedAt을 저장한다.
```

### 4. 부모 승인 및 지급

```text
1. 부모가 완료 요청을 승인한다.
2. reward-service가 상태를 PAYMENT_PENDING으로 변경한다.
3. transfer-service에 부모 지갑 -> 아이 지갑 송금을 요청한다.
4. 송금 성공 응답을 받으면 transferId를 저장한다.
5. 상태를 PAID로 변경하고 paidAt을 저장한다.
6. 아이 캐시북 수입 기록을 생성한다.
7. 보강/2차에서 아이에게 보상 지급 알림을 생성한다.
```

승인 API는 반드시 멱등하게 동작해야 한다. 같은 미션에 대해 승인 버튼이 두 번 눌려도 보상은 한 번만 지급되어야 한다.

```text
Idempotency-Key: reward-payment-{missionSubmissionId}
```

`reward-service`는 `missionSubmissionId` 기준으로 이미 지급 요청이 처리됐는지 먼저 확인하고, `transfer-service`에도 고정된 idempotency key를 전달한다.
한 미션에 재제출이 여러 번 생길 수 있으므로 지급 멱등성은 최종 승인 대상 제출 건인 `missionSubmissionId`를 기준으로 잡는다.

### 5. 부모 반려와 아이 재제출

```text
1. 부모가 반려 사유를 입력한다.
2. reward-service가 상태를 REJECTED로 변경하고 rejectionReason을 저장한다.
3. 보강/2차에서 아이에게 반려 알림을 만든다.
4. 아이는 사진/메모를 수정해 resubmit API를 호출한다.
5. 상태는 다시 SUBMITTED가 된다.
```

### 6. 캐시북 조회

```text
1. 아이 또는 부모가 캐시북 요약/내역을 조회한다.
2. reward-service가 PAID 미션과 수동 지출 기록을 기준으로 응답한다.
3. 아이가 지출 기록을 추가할 수 있다.
```

캐시북은 `cashbook_entries` 테이블을 기준으로 만든다. 보상 지급 완료 시 수입 기록을 자동 생성하고, 아이의 지출 기록은 별도 API로 추가한다.

## 상태 모델

```text
REGISTERED
  부모가 미션을 등록함

SUBMITTED
  아이가 완료 요청함

PAYMENT_PENDING
  부모가 승인했고 보상 송금 요청 중

PAYMENT_FAILED
  보상 송금 실패

PAID
  보상 송금 완료

REJECTED
  부모가 완료 요청을 거절함

CANCELED
  부모가 미션을 취소함
```

상태 전이:

```text
REGISTERED -> SUBMITTED
REGISTERED -> CANCELED
SUBMITTED -> REJECTED
SUBMITTED -> PAYMENT_PENDING -> PAID
PAYMENT_PENDING -> PAYMENT_FAILED
PAYMENT_FAILED -> PAYMENT_PENDING
PAYMENT_PENDING -> PAID
```

송금 실패 시 `PAYMENT_FAILED`와 `failureReason`을 저장하고, 같은 `reward-payment-{missionSubmissionId}` 멱등키로 재시도한다. 테스트에서는 송금 실패 후 재시도해도 중복 지급이 발생하지 않는지 확인한다.

## 데이터 모델

### Family

```text
id
parentUserId
childUserId
status
connectedAt
disconnectedAt
createdAt
updatedAt
```

### FamilyInvitation

```text
id
parentUserId
inviteCode
status
expiresAt
createdAt
updatedAt
```

### FamilyLinkRequest

```text
id
invitationId
parentUserId
childUserId
status
rejectReason
createdAt
updatedAt
```

### RewardTask

```text
id
parentUserId
childUserId
parentWalletId
childWalletId
title
description
rewardAmount
missionDate
evidenceRequired
status
rejectionReason
submittedAt
approvedAt
paidAt
transferId
failureReason
createdAt
updatedAt
```

인덱스:

```text
idx_reward_task_child_mission_date(childUserId, missionDate)
idx_reward_task_parent_mission_date(parentUserId, missionDate)
idx_reward_task_status(status)
```

제약:

```text
rewardAmount > 0
title not blank
missionDate not null
```

### RewardTaskStatus

```text
REGISTERED
SUBMITTED
PAYMENT_PENDING
PAYMENT_FAILED
PAID
REJECTED
CANCELED
```

### RewardTaskSubmission

```text
id
rewardTaskId
submitterUserId
memo
evidenceImageUrl
createdAt
```

### CashbookEntry

```text
id
childUserId
walletId
missionId
title
description
amount
entryType
createdAt
```

### Notification, 보강/2차

```text
id
userId
title
body
notificationType
readAt
createdAt
```

### FileUploadRequest, 보강/2차

```text
id
missionId
userId
fileName
contentType
fileUrl
expiresAt
createdAt
```

## API

최종 API는 `docs/api-spec.md`를 기준으로 구현한다. Gateway 외부 경로는 `/api/**`이고, reward-service 내부 경로는 prefix를 제거한 형태다.

### 가족 연결

```http
POST /families/invitations
GET /families/invitations/{inviteCode}
POST /families/link-requests
POST /families/link-requests/{requestId}/approve
POST /families/link-requests/{requestId}/reject
GET /families/me
DELETE /families/{familyId}
```

### 미션

```http
POST /missions
PATCH /missions/{missionId}
POST /missions/{missionId}/cancel
GET /missions?role=parent&status=active
GET /missions?role=child&status=active
GET /missions/{missionId}
GET /missions/calendar?year=2026&month=6&role=child
POST /missions/{missionId}/submit
POST /missions/{missionId}/approve
POST /missions/{missionId}/reject
POST /missions/{missionId}/resubmit
```

승인 API는 반드시 아래 멱등키를 사용한다.

```http
Idempotency-Key: reward-payment-{missionSubmissionId}
```

내부 transfer-service 호출:

```http
POST /transfers
Idempotency-Key: reward-payment-{missionSubmissionId}
X-User-Id: {parentUserId}
Content-Type: application/json

{
  "senderWalletId": 10,
  "receiverWalletId": 20,
  "amount": 1000
}
```

### 인증 사진 업로드 URL

```http
POST /files/mission-evidence/upload-url
X-User-Id: {childUserId}
```

### 캐시북

```http
GET /cashbook/children/{childUserId}/summary
GET /cashbook/children/{childUserId}/entries
POST /cashbook/children/{childUserId}/entries
```

캘린더는 `missionDate` 기준으로 월별 날짜에 미션을 배치한다. 보상 합계는 `PAID` 상태 미션만 수입으로 계산하고, 진행 중/제출 완료/반려 상태는 날짜별 상태 표시용으로 함께 반환한다.

### 부모 지급/정산 내역

```http
GET /parent-history/rewards
```

### 알림, 보강/2차

```http
GET /notifications/unread-count
GET /notifications
PATCH /notifications/{notificationId}/read
PATCH /notifications/read-all
```

## 멱등성과 중복 지급 방지

보상 지급은 다음 3단계로 중복을 막는다.

```text
1. reward-service mission 상태 확인
   - 이미 PAID면 기존 지급 결과를 반환한다.

2. transfer-service Idempotency-Key
   - reward-payment-{missionSubmissionId}를 사용한다.
   - 같은 제출 승인 건은 같은 송금 결과를 반환한다.

3. wallet-service reference
   - transfer-service의 reference 기반 지갑 반영 중복 방지에 의존한다.
```

승인 요청이 실패하거나 타임아웃이 발생했을 때는 `PAYMENT_PENDING` 상태와 `transferId` 여부를 기준으로 재조회/재시도한다. 동일 제출 승인 건의 재시도는 같은 idempotency key를 사용해야 한다.

## 테스트

필수 테스트:

```text
부모가 미션을 등록할 수 있다.
부모가 초대 코드를 생성할 수 있다.
아이가 초대 코드로 연결 요청을 만들 수 있다.
부모가 가족 연결 요청을 승인/거절할 수 있다.
아이는 자기 미션만 완료 요청할 수 있다.
부모는 자기 미션만 승인할 수 있다.
반려된 미션은 아이가 재제출할 수 있다.
승인 시 transfer-service가 한 번 호출된다.
승인 버튼을 두 번 눌러도 보상은 한 번만 지급된다.
이미 PAID인 미션 승인 요청은 기존 결과를 반환한다.
송금 실패 시 중복 지급 없이 실패 사유를 저장한다.
PAID 미션은 캐시북에 수입 기록으로 표시된다.
캐시북 요약은 지급 완료 미션 기준으로 계산된다.
월별 미션 캘린더는 missionDate 기준으로 조회된다.
부모/아이 권한이 맞지 않으면 403으로 실패한다.
```

보강/2차 테스트:

```text
알림 목록/읽음 처리가 동작한다.
인증 사진 업로드 URL 발급 권한을 검증한다.
아이 수동 지출 기록이 캐시북에 반영된다.
```

통합 테스트에서는 transfer-service를 mock client로 대체해도 된다. 다만 최종 smoke test에서는 실제 `transfer-service`, `wallet-service`와 연결해 부모 지갑에서 아이 지갑으로 잔액이 이동하는지 확인한다.

## 구현 순서

```text
1. reward-service 프로젝트 생성
2. application.yml, build.gradle, Dockerfile 구성
3. payflow_reward DB 추가
4. Family, FamilyInvitation, FamilyLinkRequest 구현
5. RewardTask, RewardTaskSubmission, CashbookEntry 구현
6. Repository 구현
7. TransferClient 구현
8. 가족 연결 API 구현
9. 미션 등록/수정/취소 API 구현
10. 미션 목록/상세 조회 API 구현
11. 월별 미션 캘린더 API 구현
12. 완료 제출/재제출 API 구현
13. 승인/거절 API 구현
14. 승인 시 transfer-service 송금 연동
15. 승인 멱등성 처리
16. 캐시북 요약/내역 API 구현
17. Gateway route 추가
18. docker-compose reward-service 추가
19. 단위/통합 테스트 작성
20. README 또는 데모 시나리오 갱신

보강/2차:
21. Notification, FileUploadRequest 구현
22. 부모 지급/정산 내역 API 구현
23. 알림 목록/읽음 API 구현
24. 인증 사진 업로드 URL API 구현
25. 캐시북 지출 기록 API 구현
```

## 데모 시나리오

```text
1. 부모와 아이가 회원가입/로그인한다.
2. 부모 지갑과 아이 지갑을 만든다.
3. 부모 지갑에 용돈 재원을 충전한다.
4. 부모가 "설거지 1,000원" 미션을 등록한다.
5. 아이가 오늘 할 일에서 미션을 확인한다.
6. 아이가 "다 했어요"를 누른다.
7. 부모가 승인한다.
8. 부모 지갑에서 아이 지갑으로 1,000원이 이동한다.
9. 아이 캐시북에 "설거지 +1,000원"이 기록된다.
10. 캐시북 요약에서 이번 주/이번 달 번 돈이 증가한다.
```

공모전 발표 문장:

```text
카드 세대 아이들은 돈이 어디서 오는지 체감하기 어렵습니다.
PayFlow의 아이 캐시북은 부모가 등록한 일을 아이가 수행하면 실제 용돈이 지급되고,
그 경험을 기록으로 남겨 돈이 생기는 이유를 보이게 만듭니다.
```
