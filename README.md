# PayFlow

PayFlow는 MSA 기반의 부모-자녀 미션 보상 지갑 서비스입니다.

이 프로젝트는 결제성 흐름 안에서 지갑 잔액 정합성, 송금 멱등성, 서비스 경계, 원장 기록, 장애 보상 흐름을 검증하기 위한 포트폴리오용 시스템입니다.

## 목표 흐름

```text
회원가입/로그인
-> 부모/자녀 지갑 생성
-> 부모 크레딧 충전 (Toss PG 위젯 / 오픈뱅킹)
-> 부모-자녀 연결
-> 부모가 미션 등록
-> 자녀가 완료 제출
-> 부모 승인
-> 부모 지갑에서 자녀 지갑으로 보상 송금
-> 지갑 거래 내역과 원장 기록 확인
```

## 서비스 구성

```text
Client
  |
Nginx
  |
API Gateway (JWT 검증, 사용자 헤더 주입)
  |
  +-- user-service      :8081
  +-- wallet-service    :8082
  +-- transfer-service  :8083
  +-- ledger-service    :8084
  +-- settlement-service:8085
  +-- banking-service   :8086
  +-- reward-service    :8087

Infrastructure
  +-- mysql
  +-- redis
  +-- kafka
```

| 서비스 | 책임 |
|---|---|
| api-gateway | 외부 요청 진입점, JWT 검증, 사용자 헤더 주입, 내부 헤더 위조 방지 |
| user-service | 회원가입, 로그인, 사용자 조회, 역할 관리 |
| wallet-service | 지갑 생성, 잔액 조회, 입금/출금, 거래 내역 |
| banking-service | 오픈뱅킹 계좌 연결 및 충전/출금, Toss PG 충전/취소/웹훅 처리 |
| transfer-service | 지갑 간 송금, 멱등성, 보상 환불, Transactional Outbox |
| reward-service | 가족 연결, 미션 등록/제출/승인/보상 지급, 가계부 조회 |
| ledger-service | Kafka 이벤트 기반 송금 원장, Toss 충전/취소 원장(내부 API), 실패 추적 |
| settlement-service | 정산 기능 확장 영역 |

## 핵심 설계

### 잔액 정합성

- 지갑 잔액의 source of truth는 `wallet-service`입니다.
- 잔액 변경은 DB transaction 안에서 처리합니다.
- `wallet_transactions`로 모든 잔액 변경 근거를 기록합니다.
- 같은 `idempotency_key`는 중복 반영하지 않습니다.

### 멱등성

- 송금: `transfers.idempotency_key`, `transfers.request_hash`
- 오픈뱅킹 충전/출금: `banking_transfers.idempotency_key`, `banking_transfers.request_hash`
- Toss PG 충전: `payment_charges.idempotency_key`, `payment_charges.request_hash`
- Toss 웹훅: `toss_payment_events.event_idempotency_key`
- 지갑 반영: `wallet_transactions.idempotency_key`

같은 key와 같은 body는 기존 결과를 반환하고, 같은 key와 다른 body는 `409 Conflict`로 처리합니다.

### 서비스 보안

- 외부에는 API Gateway만 노출합니다.
- 하위 서비스는 `X-Gateway-Secret` 또는 `X-Internal-Secret`이 일치하는 요청만 신뢰합니다.
- 사용자가 직접 보낸 `X-User-*`, `X-Internal-*`, `X-Gateway-Secret` 헤더는 게이트웨이에서 제거한 뒤 재주입합니다.

### 이벤트와 원장

- `transfer-service`는 송금 결과를 `outbox_events`에 저장합니다.
- `OutboxEventRelay`가 Kafka로 `transfer.completed`, `transfer.failed` 이벤트를 발행합니다.
- `ledger-service`는 `transfer.completed`를 소비해 복식 원장 라인을 생성합니다.
- `ledger-service`는 `transfer.failed`를 소비해 `transfer_failure_events`에 실패를 기록합니다.
- Toss PG 충전/취소 원장은 `banking-service`가 직접 `/ledgers/internal/payment-charge`를 호출해 기록합니다.

### 장애 보상

- 오픈뱅킹 충전: 은행 처리 중(`BANK_PROCESSING`) 상태는 스케줄러가 지수 백오프로 결과를 재조회합니다.
- 오픈뱅킹 출금: 지갑 차감 후 은행 이체 실패 시 `COMPENSATION_REQUIRED` → 수동 보상 재시도 API
- Toss PG 충전: 결제 승인 후 지갑 입금 실패 시 `COMPENSATION_REQUIRED` → `/charges/{id}/compensate`
- Toss PG 충전: 지갑 입금 성공 후 원장 기록 실패 시 `ledgerRecorded=false` → `/charges/{id}/ledger-compensate`
- 송금: 출금 후 입금 실패 시 `COMPENSATION_REQUIRED` → `/transfers/compensations/{id}/refund`

## 로컬 실행

```powershell
docker compose up --build
```

실행 후 브라우저에서 아래 주소로 접속합니다.

```text
http://localhost:19006
```

`sample-react`는 Docker 빌드 시 `FRONTEND_API_BASE_URL` 값을 `EXPO_PUBLIC_API_BASE_URL`로 주입합니다. 기본값은 `http://localhost:8080`이며, 화면은 compose로 올라간 로컬 API Gateway와 자동 연동됩니다.

현재 로컬 `.env`는 개발 편의를 위해 Hibernate `ddl-auto=update`를 사용합니다. 운영 또는 배포 검증 환경에서는 `*_JPA_DDL_AUTO=validate`로 두고 migration 도구로 스키마를 관리하세요.

## 테스트

각 Spring Boot 서비스:

```powershell
.\gradlew.bat test
```

React Native 샘플 앱:

```powershell
cd sample-react
npm run test:type
```
