# PayFlow

PayFlow는 MSA 기반 부모-자녀 미션 보상 지갑 서비스입니다.

이 프로젝트는 작은 결제 흐름 안에서 **지갑 잔액 정합성, 송금 멱등성, 서비스 경계, 원장 기록**을 구현하고 검증하는 포트폴리오입니다.

## 목표 흐름

```text
회원가입/로그인
-> 부모/자녀 지갑 생성
-> 부모 크레딧 충전
-> 부모-자녀 연결
-> 부모가 미션 등록
-> 자녀가 완료 제출
-> 부모 승인
-> 부모 지갑에서 자녀 지갑으로 보상 송금
-> 지갑 거래 이력과 원장 기록 확인
```

## 서비스 구성

```text
Client
  |
Nginx
  |
API Gateway
  |
  +-- user-service
  +-- wallet-service
  +-- banking-service
  +-- transfer-service
  +-- reward-service
  +-- ledger-service

Infrastructure
  +-- mysql
  +-- redis
  +-- kafka
```

| 서비스 | 책임 |
|---|---|
| api-gateway | 외부 요청 진입점, JWT 검증, 사용자 헤더 주입 |
| user-service | 회원가입, 로그인, 사용자 조회, 부모/자녀 역할 |
| wallet-service | 지갑 생성, 잔액 조회, 입금/출금, 잔액 변경 이력 |
| banking-service | 계좌 등록, 충전/출금 요청 상태 |
| transfer-service | 지갑 간 송금, 멱등성, wallet-service 연동 |
| reward-service | 부모-자녀 연결, 미션 등록/제출/승인/반려 |
| ledger-service | 송금 결과 원장 기록 |

## 핵심 설계

### 잔액 정합성

- 잔액의 진실은 `wallet-service`만 가집니다.
- 잔액 변경은 DB transaction 안에서 처리합니다.
- `wallet_transactions`로 잔액 변경 근거를 남깁니다.
- 같은 `referenceType` + `referenceId`는 중복 반영하지 않습니다.

### 멱등성

별도 멱등성 테이블을 만들지 않고 거래 테이블 안에서 처리합니다.

- 송금: `transfers.idempotency_key`, `transfers.request_hash`
- 충전/출금: `banking_transfers.idempotency_key`, `banking_transfers.request_hash`
- 지갑 반영: `wallet_transactions.reference_type`, `wallet_transactions.reference_id`

같은 key와 같은 body는 기존 결과를 반환하고, 같은 key와 다른 body는 `409 Conflict`를 반환합니다.

### 가족 연결

가족 연결은 하나의 테이블로 표현합니다.

```text
parent_child_links
- parent_user_id
- child_user_id
- status
```

### 미션 보상 지급

- 부모가 연결된 자녀에게 미션을 등록합니다.
- 자녀가 완료 제출을 합니다.
- 부모가 승인하면 `reward-service`가 `transfer-service`에 송금을 요청합니다.
- 보상 지급 멱등키는 `reward-payment-{missionId}`를 사용합니다.

### 원장 기록

송금 완료 후 `transfer-service`가 `ledger-service` 내부 API를 호출해 원장을 기록합니다.

```text
송금 10,000원
sender wallet   DEBIT   10,000
receiver wallet CREDIT  10,000
```

## ERD 요약

| DB | 테이블 |
|---|---|
| payflow_user | users |
| payflow_wallet | wallets, wallet_transactions |
| payflow_banking | bank_accounts, banking_transfers |
| payflow_transfer | transfers |
| payflow_reward | parent_child_links, reward_tasks |
| payflow_ledger | ledger_entries, ledger_lines |

## API 예시

송금 요청:

```http
POST /api/transfers
Idempotency-Key: 20260615-user1-transfer-001
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "senderWalletId": 1,
  "receiverWalletId": 2,
  "amount": 10000
}
```

미션 등록:

```http
POST /api/missions
Authorization: Bearer {parent_access_token}
Content-Type: application/json

{
  "childUserId": 2,
  "parentWalletId": 1,
  "childWalletId": 2,
  "title": "설거지",
  "description": "저녁 설거지하기",
  "rewardAmount": 1000,
  "missionDate": "2026-06-15"
}
```

미션 승인:

```http
POST /api/missions/100/approve
Authorization: Bearer {parent_access_token}
```

내부 송금 멱등키:

```text
reward-payment-100
```

## 실행 방법

환경 파일 생성:

```bash
cp .env.example .env
```

인프라 실행:

```bash
docker compose -f docker-compose.infra.yml up -d
```

전체 실행:

```bash
docker compose up -d
```

## 현재 구현 상태

```text
구현됨:
- user-service
- wallet-service
- api-gateway 기본 라우팅/인증

구현 예정:
- banking-service
- transfer-service
- reward-service
- ledger-service
```

## 테스트 목표

| 테스트 | 검증 내용 |
|---|---|
| 중복 지갑 반영 | 같은 reference 요청이 한 번만 반영됨 |
| 중복 송금 요청 | 같은 Idempotency-Key 요청이 중복 차감되지 않음 |
| 동시 잔액 변경 | wallet-service row lock으로 잔액 정합성 보장 |
| 송금 실패 | 실패 상태와 failureReason 저장 |
| 보상 지급 | 같은 미션 승인이 한 번만 지급됨 |
| 원장 기록 | 송금 1건당 원장 헤더 1건, 라인 2건 생성 |
| 충전/출금 | 은행/mock 응답에 따라 상태가 저장됨 |

## 문서

```text
docs/erd.md
docs/api-spec.md
docs/service-flow.md
docs/CHECKLIST.md
docs/implementation-plan/00-overview.md
docs/implementation-plan/02-database-and-migration.md
docs/implementation-plan/05-transfer-service.md
docs/implementation-plan/06-reward-service.md
docs/implementation-plan/07-open-banking-service.md
docs/implementation-plan/10-ledger-service.md
```

## Current Event Architecture

- Infrastructure now includes MySQL, Redis, and Kafka.
- `transfer-service` guards sender wallet money movement with Redis lock key `transfer:wallet-lock:{senderWalletId}`.
- `transfer-service` stores `transfer.completed` and `transfer.failed` in `outbox_events` inside the same DB transaction as transfer state changes.
- Outbox relay claims events as `PROCESSING`, publishes to Kafka, marks `PUBLISHED`, retries `FAILED`, and recovers stale `PROCESSING` events.
- `ledger-service` consumes `transfer.completed` idempotently by `transferId` and stores `ledger_entries` plus two `ledger_lines`.
- `ledger-service` consumes `transfer.failed` idempotently by `transferId` and stores `transfer_failure_events`.
- `transfer-service` exposes compensation lookup/refund APIs for `COMPENSATION_REQUIRED` transfers.
- Failure tracking APIs:
  - `GET /api/ledgers/transfer-failures`
  - `GET /api/ledgers/transfer-failures/{transferId}`
- Compensation APIs:
  - `GET /api/transfers/compensations`
  - `GET /api/transfers/compensations/{transferId}`
  - `POST /api/transfers/compensations/{transferId}/refund`
- Outbox monitoring API:
  - `GET /api/transfers/outbox/summary`

## Portfolio Highlights

- Open Banking is modeled as a financial state machine, not a simple HTTP call.
- Banking charge status uses `REQUESTED -> BANK_PROCESSING -> BANK_SUCCEEDED -> WALLET_REFLECTING -> COMPLETED`.
- Ambiguous Open Banking responses stay retryable and are finalized by result-check APIs/scheduler.
- User Open Banking tokens are encrypted before storage; raw account numbers and request body values are not persisted.
- Reference APIs marked `(x)` are exposed only as attempt endpoints and are not connected to business state changes.

Portfolio note:

```text
docs/portfolio-open-banking.md
```
