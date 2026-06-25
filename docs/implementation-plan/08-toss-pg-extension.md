# 08. Toss PG Extension Plan

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

이 문서는 기존 PayFlow 충전 시스템을 확장해서 Toss Payments PG 충전과 Open Banking 계좌 연결 방식을 함께 지원하기 위한 개발 계획이다.

핵심 방향은 기존 `banking-service`의 계좌/충전 책임을 유지하되, 외부 결제 제공자별 요청/응답/웹훅 상태를 별도 테이블에 저장하는 것이다. 지갑 잔액 변경은 계속 `wallet-service`만 수행한다.

## 목표

- 기관 담당자가 화면에서 `Toss 충전` 버튼으로 PG 결제를 시작할 수 있다.
- 사용자가 `Open Banking 계좌 연결` 버튼으로 계좌를 연결하고, 연결 계좌 기반 충전을 사용할 수 있다.
- Toss 결제 승인, 취소, 웹훅, 조회 응답을 내부 충전 상태와 분리해서 추적한다.
- 중복 결제 승인, 중복 웹훅, 결제 성공 후 지갑 반영 실패를 복구 가능 상태로 남긴다.

## 서비스 경계

### banking-service

- 연결 계좌 관리
- Open Banking 인증 URL 생성, callback 처리, 계좌 동기화
- 충전 요청 상태 관리
- Toss PG 결제 준비/승인/조회/취소 요청 관리
- Toss 웹훅 수신 및 내부 상태 전이

### wallet-service

- 지갑 입금/출금 최종 반영
- `referenceType`, `referenceId` 기반 중복 반영 방지

### ledger-service

- PG 충전 완료, PG 취소, Open Banking 충전 완료를 원장 이벤트로 기록

## 신규/확장 테이블

### payment_providers

외부 결제 제공자 설정과 운영 상태를 저장한다.

| Column | Type | Constraint | Description |
| --- | --- | --- | --- |
| id | BIGINT | PK | 제공자 ID |
| provider_code | VARCHAR(30) | UNIQUE, NOT NULL | `TOSS_PAYMENTS`, `OPEN_BANKING` |
| display_name | VARCHAR(100) | NOT NULL | 화면 표시명 |
| status | VARCHAR(30) | NOT NULL | `ACTIVE`, `DISABLED` |
| created_at | DATETIME | NOT NULL | 생성 시각 |
| updated_at | DATETIME | NOT NULL | 수정 시각 |

### payment_charges

사용자의 충전 요청을 PG/Open Banking과 무관한 내부 표준 모델로 저장한다.

| Column | Type | Constraint | Description |
| --- | --- | --- | --- |
| id | BIGINT | PK | 내부 충전 ID |
| user_id | BIGINT | NOT NULL | 사용자 ID |
| provider_code | VARCHAR(30) | NOT NULL | `TOSS_PAYMENTS`, `OPEN_BANKING` |
| charge_method | VARCHAR(30) | NOT NULL | `TOSS_WIDGET`, `OPEN_BANKING_ACCOUNT` |
| amount | DECIMAL(19,0) | NOT NULL | 충전 금액 |
| currency | VARCHAR(3) | NOT NULL | `KRW` |
| status | VARCHAR(40) | NOT NULL | 충전 상태 |
| idempotency_key | VARCHAR(255) | UNIQUE, NOT NULL | 충전 요청 멱등키 |
| request_hash | VARCHAR(255) | NOT NULL | 요청 본문 hash |
| wallet_transaction_id | BIGINT | UNIQUE | 지갑 입금 거래 ID |
| failure_code | VARCHAR(100) |  | 실패 코드 |
| failure_reason | VARCHAR(500) |  | 실패 사유 |
| created_at | DATETIME | NOT NULL | 생성 시각 |
| updated_at | DATETIME | NOT NULL | 수정 시각 |

상태값:

```text
READY
PAYMENT_PENDING
PAYMENT_APPROVED
WALLET_REFLECTING
COMPLETED
FAILED
CANCELED
PARTIAL_CANCELED
EXPIRED
UNKNOWN
COMPENSATION_REQUIRED
```

### toss_payment_orders

Toss Payments의 `orderId`, `paymentKey`, 결제 승인 결과를 저장한다.

| Column | Type | Constraint | Description |
| --- | --- | --- | --- |
| id | BIGINT | PK | Toss 주문 ID |
| payment_charge_id | BIGINT | UNIQUE, NOT NULL | 내부 충전 ID |
| toss_order_id | VARCHAR(64) | UNIQUE, NOT NULL | Toss `orderId` |
| payment_key | VARCHAR(200) | UNIQUE | Toss `paymentKey` |
| order_name | VARCHAR(100) | NOT NULL | Toss `orderName` |
| method | VARCHAR(50) |  | 결제 수단 |
| toss_status | VARCHAR(40) | NOT NULL | Toss Payment `status` |
| total_amount | DECIMAL(19,0) | NOT NULL | 최초 결제 금액 |
| balance_amount | DECIMAL(19,0) |  | 취소 가능 잔액 |
| approved_at | DATETIME |  | 승인 시각 |
| receipt_url | VARCHAR(500) |  | 영수증 URL |
| checkout_url | VARCHAR(500) |  | 결제창 URL |
| raw_response_json | JSON |  | Toss 응답 원문 |
| created_at | DATETIME | NOT NULL | 생성 시각 |
| updated_at | DATETIME | NOT NULL | 수정 시각 |

### toss_payment_events

Toss 승인/조회/취소/웹훅 이벤트를 append-only로 저장한다.

| Column | Type | Constraint | Description |
| --- | --- | --- | --- |
| id | BIGINT | PK | 이벤트 ID |
| toss_payment_order_id | BIGINT | NOT NULL | Toss 주문 ID |
| event_type | VARCHAR(50) | NOT NULL | `APPROVE_RESPONSE`, `WEBHOOK`, `CANCEL_RESPONSE`, `QUERY_RESPONSE` |
| payment_key | VARCHAR(200) |  | Toss `paymentKey` |
| transaction_key | VARCHAR(64) |  | Toss transaction key |
| toss_status | VARCHAR(40) |  | Toss 상태 |
| event_idempotency_key | VARCHAR(255) | UNIQUE | 웹훅/이벤트 중복 방지 키 |
| payload_json | JSON | NOT NULL | 원문 payload |
| received_at | DATETIME | NOT NULL | 수신 시각 |

### open_banking_authorizations

Open Banking 사용자 인증과 토큰 저장 상태를 관리한다.

| Column | Type | Constraint | Description |
| --- | --- | --- | --- |
| id | BIGINT | PK | 인증 ID |
| user_id | BIGINT | NOT NULL | 사용자 ID |
| state | VARCHAR(255) | UNIQUE, NOT NULL | OAuth state |
| status | VARCHAR(30) | NOT NULL | `REQUESTED`, `CONNECTED`, `FAILED`, `EXPIRED`, `REVOKED` |
| user_seq_no | VARCHAR(50) |  | Open Banking user seq no |
| access_token_encrypted | TEXT |  | 암호화 access token |
| refresh_token_encrypted | TEXT |  | 암호화 refresh token |
| token_expires_at | DATETIME |  | token 만료 시각 |
| failure_reason | VARCHAR(500) |  | 실패 사유 |
| created_at | DATETIME | NOT NULL | 생성 시각 |
| updated_at | DATETIME | NOT NULL | 수정 시각 |

기존 `bank_accounts`는 Open Banking 연결 결과를 저장하도록 확장한다.

추가 컬럼:

```text
provider_code
open_banking_authorization_id
fintech_use_num_encrypted
account_alias
linked_at
last_synced_at
```

## API 계획

### Toss PG 충전

```http
POST /api/payments/toss/charges
GET  /api/payments/toss/charges/{chargeId}
POST /api/payments/toss/confirm
POST /api/payments/toss/webhook
POST /api/payments/toss/payments/{paymentKey}/cancel
GET  /api/payments/toss/payments/{paymentKey}
```

`POST /api/payments/toss/charges`는 내부 충전 요청과 Toss `orderId`를 생성한다. 프론트엔드는 응답받은 `orderId`, `amount`, `orderName`, `customerKey`로 Toss 결제 위젯을 연다.

`POST /api/payments/toss/confirm`은 Toss 결제 승인 API를 호출한 뒤, 성공 시 `wallet-service`에 입금을 요청한다. 지갑 반영이 실패하면 결제는 성공했지만 내부 충전은 `COMPENSATION_REQUIRED`로 남긴다.

### Open Banking 계좌 연결/충전

```http
GET  /api/bank/openbanking/authorize-url
POST /api/bank/openbanking/callback
POST /api/bank/openbanking/accounts/sync
GET  /api/bank/accounts
POST /api/bank/deposits
POST /api/bank/transfers/{bankingTransferId}/result-check
```

기존 API를 유지하되, 계좌 등록 화면은 수기 입력보다 Open Banking 인증 URL 기반 연결을 기본 동선으로 변경한다.

## 화면 수정 계획

### 기관 홈

- 지갑 잔액 영역에 `Toss 충전` 버튼을 추가한다.
- 연결 계좌가 없으면 `Open Banking 계좌 연결` 버튼을 우선 노출한다.
- 연결 계좌가 있으면 `계좌 충전` 버튼도 함께 노출한다.

### 충전 화면

- 충전 방식 탭을 추가한다.
  - `Toss`
  - `연결 계좌`
- Toss 탭은 금액 입력 후 Toss 결제 위젯을 호출한다.
- 연결 계좌 탭은 Open Banking으로 연결된 계좌 목록을 선택하게 한다.
- 모든 충전 결과 화면은 `처리 중`, `완료`, `실패`, `복구 필요` 상태를 보여준다.

### 계좌 연결 화면

- 기존 계좌번호 직접 입력 폼은 개발/테스트용 보조 동선으로 낮춘다.
- 기본 CTA는 `Open Banking으로 계좌 연결`이다.
- callback 완료 후 연결 계좌 목록과 마지막 동기화 시각을 보여준다.

## 구현 순서

1. `payment_charges`, `toss_payment_orders`, `toss_payment_events`, `open_banking_authorizations` migration 추가
2. Toss 설정값 환경변수 추가: client key, secret key, API base URL, webhook secret
3. Toss order 생성 및 confirm client 구현
4. Toss confirm 성공 후 wallet 입금 연결
5. Toss webhook 수신, 중복 이벤트 방지, 상태 동기화 구현
6. Toss confirm 성공 후 wallet 입금 실패 건을 `COMPENSATION_REQUIRED`로 남기고 재입금 API로 복구
7. Toss 운영 조회 API로 상태별 카운트와 보상 필요 목록 제공
8. Open Banking authorize/callback/token 저장 흐름 정리
9. 계좌 동기화 결과를 `bank_accounts`에 저장
10. React 화면에 `Toss 충전`, `Open Banking 계좌 연결`, 충전 방식 탭 추가
11. 실패/재시도/보상 상태 조회 API와 화면 연결
12. 단위 테스트, contract 테스트, webhook 재전송 테스트, 지갑 반영 실패 테스트 추가

## 테스트 포인트

- 같은 `Idempotency-Key`로 Toss 충전 생성 재요청 시 같은 결과를 반환한다.
- 같은 Toss `paymentKey` confirm 재호출 시 중복 입금이 발생하지 않는다.
- 같은 웹훅 payload가 여러 번 와도 `toss_payment_events.event_idempotency_key`로 한 번만 처리한다.
- Toss 승인은 성공했지만 wallet 입금이 실패하면 `COMPENSATION_REQUIRED`로 남는다.
- Open Banking callback의 `state`가 다르면 계좌 연결을 거부한다.
- 연결 해지/토큰 만료 상태에서는 계좌 충전을 막는다.

## 2026-06-20 추가 구현 반영

13. ledger-service에 PG 충전/취소 원장 엔트리, 라인 계정 코드, 내부 기록 API를 추가한다.
14. banking-service Toss 승인/취소 성공 흐름에서 ledger-service 원장 기록을 멱등 호출한다.

추가 테스트 포인트:

- Toss 승인 성공 후 지갑 반영이 완료되면 `TOSS_CHARGE + USER_WALLET_TOPUP` 원장이 1회만 생성된다.
- Toss 취소 성공 후 지갑 차감이 완료되면 `TOSS_CANCEL + PG_CANCEL` 원장이 1회만 생성된다.

