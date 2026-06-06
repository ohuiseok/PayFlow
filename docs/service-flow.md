# PayFlow 서비스 플로우

이 문서는 부모가 크레딧을 충전하고, 자녀에게 미션과 보상을 걸고, 자녀가 미션을 완료하면 보상을 받는 전체 흐름을 정리합니다.

## 핵심 컨셉

PayFlow는 부모-자녀 미션 보상 지갑 서비스입니다.

```text
부모 크레딧 충전
-> 자녀에게 미션과 보상 등록
-> 자녀가 미션 완료 제출
-> 부모가 승인 또는 반려
-> 승인 시 부모 지갑에서 자녀 지갑으로 보상 지급
-> 자녀 캐시북과 원장에 기록
```

## 주요 사용자

| 사용자 | 역할 |
|---|---|
| 부모 | 크레딧 충전, 자녀 연결, 미션 등록, 제출 승인/반려, 지급 내역 확인 |
| 자녀 | 가족 연결 요청, 미션 확인, 완료 제출, 반려 시 재제출, 캐시북 확인 |

## 서비스 구성

| 서비스 | 책임 |
|---|---|
| api-gateway | 외부 API 진입점, JWT 검증, 사용자 헤더 주입 |
| user-service | 회원가입, 로그인, 사용자 조회. 역할/프로필/알림 설정은 보강/2차 |
| wallet-service | 부모/자녀 지갑, 잔액, 입금/출금, 중복 거래 방지 |
| banking-service | 부모 크레딧 충전, 오픈뱅킹 또는 mock 충전 상태 |
| reward-service | 가족 연결, 미션 등록/제출/승인/반려, 캐시북. 알림/파일 업로드 URL은 보강/2차 |
| transfer-service | 부모 지갑에서 자녀 지갑으로 보상 송금, 멱등성 |
| ledger-service | 보상 지급과 지갑 변경 원장 기록 |
| settlement-service | 일별 지급/수수료/정산 집계. MVP 이후 보강/2차 |
| notification-service | 알림 책임이 커질 때 reward-service에서 분리 가능한 후속 서비스 |
| file-service | 파일 업로드 책임이 커질 때 reward-service에서 분리 가능한 후속 서비스 |

현재 코드 기준으로는 `user-service`, `wallet-service` 중심 API가 구현되어 있고, 나머지는 구현 예정입니다.

## 전체 화면 흐름

```text
01 로그인
  |
10 회원가입/역할 선택
  |
11 부모 초대 코드 생성
  |
17 자녀 초대 코드 입력
  |
18 가족 연결 완료
  |
  +--------------------------+
  |                          |
부모 흐름                  자녀 흐름
  |                          |
02 부모 홈                 05 자녀 홈
  |                          |
03 크레딧 충전             12 미션 상세 상태
  |                          |
14 충전 결과               06 완료 제출
  |
04 미션 등록
  |
07 부모 승인/반려
  |
  +---- 승인 ----> 08 자녀 캐시북
  |                 19 미션 캘린더
  |
  +---- 반려 ----> 13 반려 사유/재제출

공통:
15 알림, 보강/2차
16 설정/프로필, 보강/2차
09 부모 지급/정산 내역, 보강/2차
```

## 1. 회원가입과 로그인

### 목적

부모와 자녀가 각각 계정을 만듭니다. MVP 권한은 회원 role이 아니라 Family 관계의 부모/자녀 연결 기준으로 판단합니다.

### 화면

- `01-login.svg`
- `10-signup-role.svg`

### API

```text
POST /api/users
POST /api/users/login
GET  /api/users/{userId}
```

### 처리 흐름

```text
Client
-> API Gateway
-> user-service
   -> 이메일 정규화
   -> 비밀번호 암호화
   -> 사용자 생성
   -> 로그인 시 JWT 발급
<- accessToken 반환
```

### 주요 규칙

- 이메일은 소문자/trim 정규화합니다.
- 비밀번호는 8자 이상입니다.
- 로그인 이후 API는 `Authorization: Bearer {token}`을 사용합니다.
- MVP에서는 기본 회원가입/로그인/JWT/사용자 조회를 우선합니다.
- 역할 기반 화면 진입, 프로필 수정, 알림 설정은 보강/2차로 구현합니다.
- MVP에서 부모/자녀 권한은 user role claim이 아니라 family 관계의 `parentUserId`, `childUserId` 기준으로 판단합니다.

## 2. 가족 연결

### 목적

부모와 자녀 계정을 안전하게 연결합니다.

### 화면

- `11-family-link.svg`
- `17-child-invite-code.svg`
- `18-family-connected.svg`

### API

```text
POST /api/families/invitations
GET  /api/families/invitations/{inviteCode}
POST /api/families/link-requests
POST /api/families/link-requests/{requestId}/approve
POST /api/families/link-requests/{requestId}/reject
GET  /api/families/me
DELETE /api/families/{familyId}
```

### 처리 흐름

```text
부모
-> 초대 코드 생성
-> 자녀에게 코드 전달

자녀
-> 초대 코드 입력
-> 부모 정보 확인
-> 연결 요청 생성

부모
-> 연결 요청 확인
-> 승인 또는 거절

승인 완료
-> family 관계 생성
-> 부모/자녀 홈에서 서로의 정보 노출
-> 보강/2차에서 알림 발송
```

### 상태

```text
초대 코드:
ACTIVE -> EXPIRED

연결 요청:
PENDING -> CONNECTED
PENDING -> REJECTED
```

### 주요 규칙

- 자녀는 유효한 초대 코드로만 연결 요청을 보낼 수 있습니다.
- 부모는 본인이 생성한 초대 코드에 대한 요청만 승인할 수 있습니다.
- 연결 해제 시 진행 중 미션 정책을 별도로 결정해야 합니다.

## 3. 부모 크레딧 충전

### 목적

부모가 자녀 보상 지급에 사용할 크레딧을 충전합니다.

### 화면

- `02-parent-home.svg`
- `03-credit-charge.svg`
- `14-charge-result.svg`

### API

```text
GET    /api/credits/parent/summary
GET    /api/credits/bank-accounts
POST   /api/credits/bank-accounts
DELETE /api/credits/bank-accounts/{bankAccountId}
POST   /api/credits/charges
GET    /api/credits/charges/{chargeId}
```

### 처리 흐름

```text
Client
-> API Gateway
-> banking-service
   -> 충전 요청 생성
   -> 오픈뱅킹 또는 mock 충전 처리
   -> 은행 성공 확정 시 wallet-service 입금 호출
      POST /wallets/{walletId}/deposit
      referenceType = OPEN_BANKING_CHARGE
      referenceId = bankTranId
   -> 충전 상태 완료로 변경
<- 충전 결과 반환
```

### 상태

```text
REQUESTED -> BANK_REQUESTED -> BANK_PROCESSING -> BANK_SUCCEEDED
                                           -> WALLET_REFLECTING -> COMPLETED
REQUESTED/BANK_REQUESTED/BANK_PROCESSING -> FAILED
BANK_PROCESSING -> UNKNOWN
BANK_SUCCEEDED/WALLET_REFLECTING -> BANK_SUCCESS_BUT_WALLET_FAILED
```

### 주요 규칙

- 충전 금액은 1원 이상 10,000,000원 이하의 정수입니다.
- 같은 충전 요청이 중복 반영되지 않도록 `Idempotency-Key`와 `bankTranId`를 사용합니다.
- 은행 성공 후 wallet-service 입금 호출이 실패하면 `BANK_SUCCESS_BUT_WALLET_FAILED`로 저장하고, 재처리 워커가 같은 `bankTranId`로 deposit을 재시도합니다.
- 실제 계좌번호는 원문 저장을 피하고 마스킹 값 또는 외부 식별자를 사용합니다.

## 4. 미션 등록

### 목적

부모가 자녀에게 할 일, 보상 금액, 미션 수행 날짜를 등록합니다.

### 화면

- `04-mission-create.svg`
- `02-parent-home.svg`
- `05-child-home.svg`
- `19-mission-calendar.svg`

### API

```text
POST  /api/missions
PATCH /api/missions/{missionId}
POST  /api/missions/{missionId}/cancel
GET   /api/missions?role=parent&status=active
GET   /api/missions?role=child&status=active
GET   /api/missions/{missionId}
GET   /api/missions/calendar?year=2026&month=6&role=child
```

### 처리 흐름

```text
부모
-> 자녀 선택
-> 미션명, 설명, 보상 금액, 수행 날짜 입력
-> reward-service 미션 생성
-> MVP에서는 미션 목록/상세에서 조회 가능하게 저장
-> 보강/2차에서 자녀에게 미션 도착 알림 생성

자녀
-> 자녀 홈에서 새 미션 확인
```

### 상태

```text
REGISTERED
```

### 주요 규칙

- 부모는 연결된 자녀에게만 미션을 등록할 수 있습니다.
- 미션 등록 시 보상 금액은 부모 지갑 잔액 이하인지 검증하거나 경고할 수 있습니다.
- 최종 잔액 검증은 부모 승인/송금 시점에 transfer-service와 wallet-service에서 수행합니다.
- 자녀가 제출하기 전까지만 수정/취소할 수 있습니다.
- 미션 수행 날짜는 월별 캘린더 조회의 기준 날짜로 사용합니다.
- 목록/캘린더 API의 `role=parent|child` 파라미터는 권한 판단 기준이 아니라 조회 관점(view mode)입니다.
- MVP 권한 판단은 Family 관계의 `parentUserId`, `childUserId`와 `X-User-Id`를 비교해 수행합니다.

## 5. 자녀 미션 완료 제출

### 목적

자녀가 미션 완료 사실을 메모와 사진으로 제출합니다.

### 화면

- `12-mission-detail-status.svg`
- `06-mission-submit.svg`

### API

```text
POST /api/missions/{missionId}/submit
GET  /api/missions/{missionId}

보강/2차:
POST /api/files/mission-evidence/upload-url
```

### 처리 흐름

```text
자녀
-> 미션 상세 확인
-> 메모와 선택적 evidenceImageUrl로 완료 제출

reward-service
-> 미션 상태 SUBMITTED 변경
-> 보강/2차에서 부모에게 승인 요청 알림 생성
```

### 상태

```text
REGISTERED -> SUBMITTED
```

### 주요 규칙

- 자녀 본인에게 할당된 미션만 제출할 수 있습니다.
- 제출 이후 부모 승인 전에는 제출 수정 가능 여부를 정책으로 정합니다.
- MVP에서는 파일 업로드 URL 발급 없이 `evidenceImageUrl`을 선택값으로 둘 수 있습니다.
- 사진 업로드 URL 발급은 보강/2차에서 구현합니다.

## 6. 부모 승인과 보상 지급

### 목적

부모가 자녀 제출물을 확인하고 승인하면 보상을 지급합니다.

### 화면

- `07-parent-approval.svg`
- `08-cashbook.svg`
- `09-parent-history.svg`

### API

```text
POST /api/missions/{missionId}/approve
GET  /api/cashbook/children/{childUserId}/summary
GET  /api/cashbook/children/{childUserId}/entries
GET  /api/parent-history/rewards
```

### 처리 흐름

```text
부모
-> 제출 내용 확인
-> 승인
-> reward-service 승인 요청

reward-service
-> 미션 상태 PAYMENT_PENDING 또는 처리 중 상태로 변경
-> transfer-service 보상 지급 요청
   Idempotency-Key = reward-payment-{missionSubmissionId}

transfer-service
-> 부모 지갑 출금
-> 자녀 지갑 입금
-> 송금 완료 상태 저장
-> outbox 이벤트 저장

outbox publisher
-> Kafka TransferCompleted 발행

ledger-service
-> 이벤트 수신
-> 부모 지갑 -금액, 자녀 지갑 +금액 원장 기록

reward-service
-> 미션 상태 PAID
-> 자녀 캐시북 기록 생성
-> 보강/2차에서 자녀에게 보상 지급 알림 생성
```

### 상태

```text
SUBMITTED -> PAYMENT_PENDING -> PAID
```

### 주요 규칙

- 승인 요청은 반드시 멱등키를 사용합니다.
- 같은 미션은 한 번만 지급되어야 합니다.
- 부모 지갑 잔액 부족 시 지급 실패 상태와 재시도 정책이 필요합니다.
- 캐시북은 “자녀가 어떤 미션으로 돈을 벌었는지”를 보여주는 사용자 기록입니다.

## 7. 부모 반려와 자녀 재제출

### 목적

제출물이 부족할 때 부모가 사유를 남기고 자녀가 다시 제출합니다.

### 화면

- `13-reject-resubmit.svg`
- `12-mission-detail-status.svg`
- `06-mission-submit.svg`

### API

```text
POST /api/missions/{missionId}/reject
POST /api/missions/{missionId}/resubmit
GET  /api/missions/{missionId}
```

### 처리 흐름

```text
부모
-> 반려 사유 입력
-> 미션 상태 REJECTED 변경
-> 보강/2차에서 자녀에게 반려 알림 생성

자녀
-> 반려 사유 확인
-> 사진/메모 수정
-> 재제출
-> 미션 상태 SUBMITTED 변경
-> 보강/2차에서 부모에게 승인 요청 알림 생성
```

### 상태

```text
SUBMITTED -> REJECTED -> SUBMITTED
```

### 주요 규칙

- 반려 사유는 자녀 화면에 노출됩니다.
- 재제출 가능 횟수 제한 여부는 정책으로 정합니다.

## 8. 자녀 캐시북

### 목적

자녀가 미션으로 번 돈과 사용 기록을 확인합니다.

### 화면

- `08-cashbook.svg`
- `05-child-home.svg`
- `19-mission-calendar.svg`

### API

```text
GET  /api/cashbook/children/{childUserId}/summary
GET  /api/cashbook/children/{childUserId}/entries
POST /api/cashbook/children/{childUserId}/entries
GET  /api/missions/calendar?year=2026&month=6&role=child
```

### 처리 흐름

```text
보상 지급 완료
-> 캐시북 수입 기록 생성

자녀
-> 캐시북 요약 조회
-> 캐시북 내역 조회
-> 월별 미션 캘린더 조회
-> 필요 시 지출 기록 작성
```

### 주요 규칙

- 자녀 본인과 연결된 부모만 조회할 수 있습니다.
- 지출 기록이 실제 지갑 차감인지, 단순 기록인지는 정책으로 정합니다.
- 캘린더는 `missionDate` 기준으로 날짜별 미션과 지급 상태를 표시합니다.

## 9. 부모 지급/정산 내역

보강/2차 범위입니다. MVP에서는 원장 조회와 캐시북 조회로 지급 결과를 확인합니다.

### 목적

부모가 크레딧 충전, 미션 보상 지급, 정산 상태를 확인합니다.

### 화면

- `09-parent-history.svg`

### API

```text
GET /api/parent-history/rewards
```

### 처리 흐름

```text
부모
-> 지급/정산 내역 조회

reward-service 또는 settlement-service
-> 월별 지급 합계
-> 미션별 지급 내역
-> 원장 기록 여부
-> 정산 대기/완료 상태 반환
```

### 주요 규칙

- 지급 내역은 원장 기록과 대조 가능해야 합니다.
- 정산은 일별 배치로 처리할 수 있습니다.

## 10. 알림

보강/2차 범위입니다. MVP에서는 각 목록/상세 API의 상태값으로 사용자에게 필요한 정보를 표시합니다.

### 목적

미션 도착, 제출, 승인, 반려, 지급, 충전 결과를 사용자에게 알려줍니다.

### 화면

- `15-notifications.svg`

### API

```text
GET   /api/notifications/unread-count
GET   /api/notifications
PATCH /api/notifications/{notificationId}/read
PATCH /api/notifications/read-all
```

### 알림 발생 지점

| 이벤트 | 수신자 |
|---|---|
| 가족 연결 요청 | 부모 |
| 가족 연결 승인/거절 | 자녀 |
| 미션 등록 | 자녀 |
| 미션 제출 | 부모 |
| 미션 반려 | 자녀 |
| 보상 지급 완료 | 자녀 |
| 충전 완료/실패 | 부모 |

## 11. 설정과 프로필

보강/2차 범위입니다. MVP에서는 기본 사용자 조회와 인증 흐름을 우선합니다.

### 목적

프로필, 가족 관리, 지갑/충전 계좌, 알림 설정, 로그아웃을 처리합니다.

### 화면

- `16-settings-profile.svg`

### API

```text
GET    /api/settings/profile
PATCH  /api/settings/profile
GET    /api/settings/notification-preferences
PATCH  /api/settings/notification-preferences
DELETE /api/families/{familyId}
POST   /api/users/logout
```

### 주요 규칙

- JWT 블랙리스트를 사용하지 않는다면 로그아웃은 클라이언트 토큰 삭제만으로 처리할 수 있습니다.
- 가족 연결 해제 시 진행 중 미션, 지급 대기 보상 처리 정책이 필요합니다.

## 주요 상태 모델

### 미션 상태

```text
REGISTERED -> SUBMITTED -> PAYMENT_PENDING -> PAID
REGISTERED -> CANCELED
SUBMITTED  -> REJECTED -> SUBMITTED
PAYMENT_PENDING -> PAYMENT_FAILED
PAYMENT_FAILED  -> PAYMENT_PENDING
```

| 상태 | 화면 라벨 | 설명 |
|---|---|---|
| REGISTERED | 등록 완료 | 부모가 미션을 등록함 |
| SUBMITTED | 제출 완료 | 자녀가 완료 인증을 제출함 |
| REJECTED | 반려됨 | 부모가 제출을 반려함 |
| PAYMENT_PENDING | 지급 처리 중 | 승인 후 보상 송금 중 |
| PAID | 지급 완료 | 보상 지급 완료 |
| PAYMENT_FAILED | 지급 실패 | 잔액 부족 또는 송금 실패 |
| CANCELED | 취소됨 | 부모가 미션을 취소함 |

### 가족 연결 상태

```text
ACTIVE -> EXPIRED
PENDING -> CONNECTED
PENDING -> REJECTED
CONNECTED -> DISCONNECTED
```

### 충전 상태

```text
REQUESTED -> BANK_REQUESTED -> BANK_PROCESSING -> BANK_SUCCEEDED
                                           -> WALLET_REFLECTING -> COMPLETED
REQUESTED/BANK_REQUESTED/BANK_PROCESSING -> FAILED
BANK_PROCESSING -> UNKNOWN
BANK_SUCCEEDED/WALLET_REFLECTING -> BANK_SUCCESS_BUT_WALLET_FAILED
```

### 보상 지급 송금 상태

```text
REQUESTED -> PROCESSING -> COMPLETED
                         -> FAILED
                         -> COMPENSATION_REQUIRED -> ROLLED_BACK
```

## 데이터 소유권

| 데이터 | 소유 서비스 |
|---|---|
| 사용자/역할 | user-service |
| 가족 연결 | reward-service 또는 family-service |
| 부모/자녀 지갑 잔액 | wallet-service |
| 충전 요청/계좌 연동 상태 | banking-service |
| 미션/제출/승인/반려 | reward-service |
| 보상 송금 상태 | transfer-service |
| 캐시북 | reward-service |
| 원장 | ledger-service |
| 정산 | settlement-service |
| 알림 | 보강/2차. reward-service 내부 notification 모듈, 후속 분리 시 notification-service |
| 인증 사진 파일 URL | 보강/2차. reward-service 내부 file 모듈, 후속 분리 시 file-service |

## 권한 규칙

| 동작 | 허용 사용자 |
|---|---|
| 부모 크레딧 충전 | 부모 본인 |
| 자녀 연결 요청 | 자녀 본인 |
| 연결 요청 승인/거절 | 초대 코드를 만든 부모 |
| 미션 등록 | 연결된 부모 |
| 미션 조회 | 연결된 부모 또는 담당 자녀 |
| 미션 제출/재제출 | 담당 자녀 |
| 미션 승인/반려 | 미션을 등록한 부모 |
| 캐시북 조회 | 자녀 본인 또는 연결된 부모 |
| 지급 내역 조회 | 부모 본인 |
| 설정 변경 | 사용자 본인 |

## 멱등성 규칙

아래 동작은 중복 요청 방지가 필요합니다.

| 동작 | 멱등 기준 |
|---|---|
| 크레딧 충전 반영 | `bankTranId` |
| 미션 승인/보상 지급 | `reward-payment-{missionSubmissionId}` |
| 지갑 입금/출금 | `referenceType` + `referenceId` |
| 원장 이벤트 처리 | `sourceEventId` unique |

## 실패와 복구

### 충전 실패

```text
충전 요청 생성
-> 외부 은행 응답 실패
-> BankingTransferStatus = FAILED
-> 보강/2차에서 부모에게 실패 알림
-> 지갑 잔액 변경 없음
```

### 은행 성공 후 지갑 반영 실패

```text
오픈뱅킹 출금이체 성공 확정
-> wallet-service deposit 호출 실패
-> BankingTransferStatus = BANK_SUCCESS_BUT_WALLET_FAILED
-> nextResultCheckAt 설정
-> 재처리 워커가 같은 bankTranId로 deposit 재시도
-> wallet-service는 referenceType/referenceId 중복 방어로 중복 입금을 막음
-> deposit 성공 시 COMPLETED
```

### 보상 지급 실패

```text
부모 승인
-> 부모 지갑 출금 실패 또는 자녀 지갑 입금 실패
-> MissionStatus = PAYMENT_FAILED
-> 재시도 가능 상태 유지
-> 보강/2차에서 부모에게 실패 알림
```

### 이벤트 발행 실패

```text
DB 트랜잭션 안에서 outbox_event 저장
-> Kafka 발행 실패
-> outbox 미발행 상태 유지
-> publisher 재시도
```

## 구현 우선순위

1. user-service/wallet-service/api-gateway 최소 기반 확인
2. transfer-service 송금 상태 머신과 멱등성 구현
3. transfer-service OutboxEvent 저장 구현
4. Outbox publisher와 Kafka 이벤트 발행 구현
5. ledger-service `transfer.completed` 소비와 `sourceEventId` unique 기반 원장 기록 구현
6. banking-service Mock PG 또는 오픈뱅킹 테스트베드 충전 구현
7. reward-service 가족/미션/승인/보상 지급/캐시북 구현
8. 실패 복구, 재처리 워커, smoke test 정리
9. Gateway reward/credits route 보강
10. settlement, 알림, 파일 업로드 URL, 프로필/설정은 보강/2차로 구현
