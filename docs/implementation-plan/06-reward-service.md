# 06. Reward Service

`reward-service`는 PayFlow의 기존 지갑/송금 인프라 위에 얹는 어린이 금융 습관 기능이다.

부모가 실제 보상금이 걸린 일을 등록하고, 아이가 일을 완료하면 부모 승인 후 부모 지갑에서 아이 지갑으로 용돈이 지급된다. 캘린더는 아이가 "무엇을 해서 돈을 벌었는지"를 날짜별로 보여주는 학습 기록 역할을 한다.

핵심 메시지:

```text
아이에게 돈은 충전되는 숫자가 아니라, 내가 만든 가치의 기록이 된다.
```

## 목표

구현할 기능:

```text
부모가 보상 미션을 등록한다.
아이가 미션 완료를 요청한다.
부모가 완료 요청을 승인하거나 거절한다.
승인되면 부모 지갑에서 아이 지갑으로 실제 용돈이 송금된다.
지급 완료된 미션은 캘린더에 기록된다.
월별로 아이가 번 돈과 수행한 일을 조회한다.
```

구현하지 않을 기능:

```text
복잡한 가족 권한 관리
사진/영상 인증 저장소
반복 미션 자동 생성
아이 전용 카드 발급
외부 PG/카드사 실결제 연동
교육 콘텐츠 추천
```

## 서비스 경계

`reward-service`는 미션과 보상 지급 흐름의 진실만 가진다. 지갑 잔액의 진실은 `wallet-service`, 송금 상태의 진실은 `transfer-service`, 원장 기록의 진실은 `ledger-service`가 가진다.

```text
reward-service
- 보상 미션 등록
- 완료 요청
- 부모 승인/거절
- 보상 지급 요청
- 캘린더 조회

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

초기 구현에서는 `reward-service`가 OpenFeign으로 `transfer-service`를 호출한다. 보상 지급 성공 이벤트를 별도로 구독하는 구조는 2차 범위로 둔다.

## 핵심 흐름

### 1. 부모 미션 등록

```text
1. 부모가 아이, 보상금, 수행일, 제목을 입력한다.
2. reward-service가 부모/아이 식별자와 지갑 ID를 저장한다.
3. 상태는 REGISTERED가 된다.
```

초기 구현에서는 부모/아이 관계 검증을 단순화하고, 요청의 `parentUserId`는 Gateway의 `X-User-Id`를 신뢰한다. `childUserId`, `parentWalletId`, `childWalletId`는 요청으로 받되, 추후 user-service 관계 모델로 검증할 수 있게 분리한다.

### 2. 아이 완료 요청

```text
1. 아이가 미션을 완료했다고 요청한다.
2. reward-service가 미션의 childUserId와 요청 사용자 ID를 비교한다.
3. 상태를 SUBMITTED로 변경한다.
4. submittedAt을 저장한다.
```

### 3. 부모 승인 및 지급

```text
1. 부모가 완료 요청을 승인한다.
2. reward-service가 상태를 PAYMENT_PENDING으로 변경한다.
3. transfer-service에 부모 지갑 -> 아이 지갑 송금을 요청한다.
4. 송금 성공 응답을 받으면 transferId를 저장한다.
5. 상태를 PAID로 변경하고 paidAt을 저장한다.
```

승인 API는 반드시 멱등하게 동작해야 한다. 같은 미션에 대해 승인 버튼이 두 번 눌려도 보상은 한 번만 지급되어야 한다.

```text
Idempotency-Key: reward-payment-{taskId}
```

`reward-service`는 `taskId` 기준으로 이미 `PAID`인지 먼저 확인하고, `transfer-service`에도 고정된 idempotency key를 전달한다.

### 4. 캘린더 조회

```text
1. 아이 또는 부모가 월별 캘린더를 조회한다.
2. reward-service가 해당 월의 REGISTERED/SUBMITTED/PAID 미션을 조회한다.
3. 지급 완료 금액 합계와 날짜별 미션을 반환한다.
```

캘린더는 별도 테이블 없이 `reward_task`를 기준으로 만든다. 초기 기준 날짜는 `taskDate`를 사용하고, 실제 지급 시점은 `paidAt`으로 별도 제공한다.

## 상태 모델

```text
REGISTERED
  부모가 미션을 등록함

SUBMITTED
  아이가 완료 요청함

PAYMENT_PENDING
  부모가 승인했고 보상 송금 요청 중

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
PAYMENT_PENDING -> PAID
```

초기 구현에서는 `PAYMENT_PENDING` 상태에서 송금 실패 시 `PAYMENT_FAILED`를 추가하지 않고, `failureReason`을 저장한 뒤 운영자 재처리 대상으로 남길 수 있다. 다만 테스트에서는 송금 실패 시 중복 지급이 발생하지 않는지 확인한다.

## 데이터 모델

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
taskDate
status
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
idx_reward_task_child_date(childUserId, taskDate)
idx_reward_task_parent_date(parentUserId, taskDate)
idx_reward_task_status(status)
```

제약:

```text
rewardAmount > 0
title not blank
taskDate not null
```

### RewardTaskStatus

```text
REGISTERED
SUBMITTED
PAYMENT_PENDING
PAID
REJECTED
CANCELED
```

## API

### 미션 등록

```http
POST /rewards/tasks
X-User-Id: 1
Content-Type: application/json

{
  "childUserId": 2,
  "parentWalletId": 10,
  "childWalletId": 20,
  "title": "설거지",
  "description": "저녁 설거지하기",
  "rewardAmount": 1000,
  "taskDate": "2026-06-03"
}
```

응답:

```json
{
  "taskId": 100,
  "status": "REGISTERED",
  "title": "설거지",
  "rewardAmount": 1000,
  "taskDate": "2026-06-03"
}
```

### 아이 미션 목록

```http
GET /rewards/tasks?childUserId=2&from=2026-06-01&to=2026-06-30
X-User-Id: 2
```

### 완료 요청

```http
POST /rewards/tasks/{taskId}/submit
X-User-Id: 2
```

### 승인 및 지급

```http
POST /rewards/tasks/{taskId}/approve
X-User-Id: 1
Idempotency-Key: reward-task-100-approve
```

내부 transfer-service 호출:

```http
POST /transfers
Idempotency-Key: reward-payment-100
X-User-Id: 1
Content-Type: application/json

{
  "senderWalletId": 10,
  "receiverWalletId": 20,
  "amount": 1000
}
```

### 거절

```http
POST /rewards/tasks/{taskId}/reject
X-User-Id: 1
Content-Type: application/json

{
  "reason": "아직 완료되지 않았어요"
}
```

### 캘린더 조회

```http
GET /rewards/calendar?childUserId=2&year=2026&month=6
X-User-Id: 2
```

응답:

```json
{
  "year": 2026,
  "month": 6,
  "totalEarnedAmount": 17500,
  "paidTaskCount": 8,
  "entries": [
    {
      "date": "2026-06-03",
      "taskId": 100,
      "title": "설거지",
      "amount": 1000,
      "status": "PAID",
      "paidAt": "2026-06-03T20:10:00"
    }
  ]
}
```

## 멱등성과 중복 지급 방지

보상 지급은 다음 3단계로 중복을 막는다.

```text
1. reward-service task 상태 확인
   - 이미 PAID면 기존 지급 결과를 반환한다.

2. transfer-service Idempotency-Key
   - reward-payment-{taskId}를 사용한다.
   - 같은 task는 같은 송금 결과를 반환한다.

3. wallet-service reference
   - transfer-service의 reference 기반 지갑 반영 중복 방지에 의존한다.
```

승인 요청이 실패하거나 타임아웃이 발생했을 때는 `PAYMENT_PENDING` 상태와 `transferId` 여부를 기준으로 재조회/재시도한다. 동일 미션의 재시도는 같은 idempotency key를 사용해야 한다.

## 테스트

필수 테스트:

```text
부모가 미션을 등록할 수 있다.
아이는 자기 미션만 완료 요청할 수 있다.
부모는 자기 미션만 승인할 수 있다.
승인 시 transfer-service가 한 번 호출된다.
승인 버튼을 두 번 눌러도 보상은 한 번만 지급된다.
이미 PAID인 미션 승인 요청은 기존 결과를 반환한다.
송금 실패 시 중복 지급 없이 실패 사유를 저장한다.
PAID 미션은 캘린더에 표시된다.
월별 캘린더 합계가 지급 완료 미션 기준으로 계산된다.
부모/아이 권한이 맞지 않으면 403으로 실패한다.
```

통합 테스트에서는 transfer-service를 mock client로 대체해도 된다. 다만 최종 smoke test에서는 실제 `transfer-service`, `wallet-service`와 연결해 부모 지갑에서 아이 지갑으로 잔액이 이동하는지 확인한다.

## 구현 순서

```text
1. reward-service 프로젝트 생성
2. application.yml, build.gradle, Dockerfile 구성
3. payflow_reward DB 추가
4. RewardTask, RewardTaskStatus 구현
5. Repository 구현
6. TransferClient 구현
7. 미션 등록 API 구현
8. 아이 미션 목록/상세 조회 API 구현
9. 완료 요청 API 구현
10. 승인/거절 API 구현
11. 승인 시 transfer-service 송금 연동
12. 승인 멱등성 처리
13. 캘린더 조회 API 구현
14. Gateway route 추가
15. docker-compose reward-service 추가
16. 단위/통합 테스트 작성
17. README 또는 데모 시나리오 갱신
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
9. 아이 캘린더에 "설거지 +1,000원"이 기록된다.
10. 월별 합계에서 이번 달 번 돈이 증가한다.
```

공모전 발표 문장:

```text
카드 세대 아이들은 돈이 어디서 오는지 체감하기 어렵습니다.
PayFlow의 용돈 캘린더는 부모가 등록한 일을 아이가 수행하면 실제 용돈이 지급되고,
그 경험을 캘린더에 남겨 돈이 생기는 이유를 보이게 만듭니다.
```
