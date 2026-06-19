# PayFlow

PayFlow는 MSA 기반의 부모-자녀 미션 보상 지갑 서비스입니다.

이 프로젝트는 결제성 흐름 안에서 지갑 잔액 정합성, 송금 멱등성, 서비스 경계, 원장 기록, 장애 보상 흐름을 검증하기 위한 포트폴리오용 시스템입니다.

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
-> 지갑 거래 내역과 원장 기록 확인
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
  +-- settlement-service

Infrastructure
  +-- mysql
  +-- redis
  +-- kafka
```

| 서비스 | 책임 |
|---|---|
| api-gateway | 외부 요청 진입점, JWT 검증, 사용자 헤더 주입 |
| user-service | 회원가입, 로그인, 사용자 조회, 역할 관리 |
| wallet-service | 지갑 생성, 잔액 조회, 입금/출금, 거래 내역 |
| banking-service | 계좌 등록, 오픈뱅킹 충전/출금 요청 상태 관리 |
| transfer-service | 지갑 간 송금, 멱등성, 보상 처리, outbox 이벤트 |
| reward-service | 가족 연결, 미션 등록/제출/승인/보상 지급 |
| ledger-service | 송금 완료 이벤트 기반 원장 기록 |
| settlement-service | 정산 기능 확장 영역 |

## 핵심 설계

### 잔액 정합성

- 지갑 잔액의 source of truth는 `wallet-service`입니다.
- 잔액 변경은 DB transaction 안에서 처리합니다.
- `wallet_transactions`로 모든 잔액 변경 근거를 기록합니다.
- 같은 `referenceType` + `referenceId`는 중복 반영하지 않습니다.

### 멱등성

- 송금: `transfers.idempotency_key`, `transfers.request_hash`
- 충전/출금: `banking_transfers.idempotency_key`, `banking_transfers.request_hash`
- 지갑 반영: `wallet_transactions.reference_type`, `wallet_transactions.reference_id`

같은 key와 같은 body는 기존 결과를 반환하고, 같은 key와 다른 body는 `409 Conflict`로 처리합니다.

### 서비스 보안

- 외부에는 API Gateway만 노출하는 것을 기본으로 합니다.
- 하위 서비스는 `X-Gateway-Secret` 또는 `X-Internal-Secret`이 맞는 요청만 신뢰합니다.
- 사용자가 직접 보낸 `X-User-*`, `X-Internal-*`, `X-Gateway-Secret` 헤더는 게이트웨이에서 제거한 뒤 재주입합니다.

### 이벤트와 원장

- `transfer-service`는 송금 결과를 outbox에 저장합니다.
- outbox relay가 Kafka로 `transfer.completed`, `transfer.failed` 이벤트를 발행합니다.
- `ledger-service`는 `transfer.completed` 이벤트를 소비해 복식 원장 라인을 생성합니다.

## 로컬 실행

```powershell
docker compose up --build
```

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
