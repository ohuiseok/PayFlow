# PayFlow

PayFlow는 MSA 기반의 부모-자녀 미션 보상 지갑 서비스입니다.

결제성 흐름 안에서 지갑 잔액 정합성, 송금 멱등성, 서비스 경계, 원장 기록, 장애 보상 흐름을 구현한 포트폴리오용 시스템입니다.

## 목표 흐름

```text
회원가입/로그인
-> 부모/자녀 지갑 생성 (자동)
-> 부모 크레딧 충전 (Toss PG / 오픈뱅킹)
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
Nginx (:80)
  |
API Gateway (:8080)  — JWT 검증, 사용자 헤더 주입
  |
  +-- user-service      :8081
  +-- wallet-service    :8082
  +-- transfer-service  :8083
  +-- ledger-service    :8084
  +-- settlement-service:8085
  +-- banking-service   :8086
  +-- reward-service    :8087

Infrastructure
  +-- MySQL   :3306
  +-- Redis   :6379
  +-- Kafka   :9092
```

| 서비스 | 책임 |
|---|---|
| api-gateway | 외부 요청 진입점, JWT 검증, 사용자 헤더 주입, 내부 헤더 위조 방지 |
| user-service | 회원가입(초대 코드 기반 역할 결정), 로그인, JWT 발급 |
| wallet-service | 지갑 생성, 잔액 조회, 입금/출금, 거래 내역 |
| banking-service | 오픈뱅킹 계좌 연결 및 충전/출금, Toss PG 충전/취소/웹훅 처리 |
| transfer-service | 지갑 간 송금, 멱등성, 보상 환불, Transactional Outbox, ShedLock |
| reward-service | 가족 연결, 미션 등록/제출/승인/보상 지급, 가계부 조회 |
| ledger-service | Kafka 이벤트 기반 송금 원장, Toss 충전/취소 원장, DLT 처리 |
| settlement-service | 정산 기능 확장 영역 |

## 빠른 시작

### 사전 조건

- Docker Desktop (Docker Compose v2)
- `.env` 파일 (`.env.example` 복사 후 시크릿 교체)

```bash
cp .env.example .env
# .env에서 아래 값을 반드시 교체한다:
# MYSQL_ROOT_PASSWORD, MYSQL_PASSWORD
# JWT_SECRET (32자 이상 랜덤 문자열)
# INTERNAL_SERVICE_SECRET
# PARENT_INVITE_CODE (PARENT 역할 가입에 필요한 초대 코드)
```

### 실행

```bash
# 인프라(MySQL, Redis, Kafka) 먼저 기동
docker compose -f docker-compose.infra.yml up -d

# 전체 서비스 기동
docker compose up -d

# 로그 확인
docker compose logs -f api-gateway
docker compose logs -f user-service
```

### 상태 확인

```bash
# Compose 설정 검증
docker compose config --quiet && echo "CONFIG OK"

# 게이트웨이 헬스 체크
curl http://localhost/health

# 서비스별 헬스 체크
curl http://localhost:8081/actuator/health   # user-service
curl http://localhost:8082/actuator/health   # wallet-service
```

## 주요 API 예시

기본 URL: `http://localhost` (nginx → api-gateway 프록시)

### 회원가입 / 로그인

```bash
# 부모 가입 (초대 코드 필요 — .env의 PARENT_INVITE_CODE 값)
curl -X POST http://localhost/api/users \
  -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"01011112222","password":"password1234","name":"Parent","inviteCode":"PAYFLOW-PARENT-2024"}'
# → {"userId":1,"role":"PARENT",...}

# 자녀 가입 (초대 코드 없음 → CHILD 역할 자동 부여)
curl -X POST http://localhost/api/users \
  -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"01033334444","password":"password1234","name":"Child"}'
# → {"userId":2,"role":"CHILD",...}

# 로그인 → JWT 획득
curl -X POST http://localhost/api/users/login \
  -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"01011112222","password":"password1234"}'
# → {"accessToken":"eyJ...","tokenType":"Bearer","expiresIn":86400000}
```

### 지갑 / 충전

```bash
# 지갑 잔액 조회
curl http://localhost/api/wallets/me \
  -H 'Authorization: Bearer {TOKEN}'

# 은행 계좌 등록
curl -X POST http://localhost/api/bank/accounts \
  -H 'Authorization: Bearer {TOKEN}' \
  -H 'Content-Type: application/json' \
  -d '{"bankCode":"004","accountNumber":"123456789012","accountHolderName":"Parent"}'

# 지갑 충전 (오픈뱅킹 입금)
curl -X POST http://localhost/api/bank/deposits \
  -H 'Authorization: Bearer {TOKEN}' \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: deposit-001' \
  -d '{"bankAccountId":1,"amount":50000}'
```

### 미션 보상 흐름

```bash
# 1. 부모-자녀 연결
curl -X POST http://localhost/api/families/links \
  -H 'Authorization: Bearer {PARENT_TOKEN}' \
  -H 'Content-Type: application/json' \
  -d '{"childUserId":2}'

# 2. 미션 생성 (부모)
curl -X POST http://localhost/api/missions \
  -H 'Authorization: Bearer {PARENT_TOKEN}' \
  -H 'Content-Type: application/json' \
  -d '{"childUserId":2,"title":"방 청소하기","description":"청소 후 사진 제출","rewardAmount":3000}'

# 3. 미션 제출 (자녀)
curl -X PATCH http://localhost/api/missions/1/submit \
  -H 'Authorization: Bearer {CHILD_TOKEN}' \
  -H 'Content-Type: application/json' \
  -d '{"submissionNote":"완료했습니다"}'

# 4. 미션 승인 (부모)
curl -X PATCH http://localhost/api/missions/1/approve \
  -H 'Authorization: Bearer {PARENT_TOKEN}'

# 5. 보상 지급 (부모) — 부모 지갑 → 자녀 지갑 송금
curl -X POST http://localhost/api/missions/1/pay \
  -H 'Authorization: Bearer {PARENT_TOKEN}'
# → {"status":"PAID","transferId":100,...}
```

## 테스트 실행

각 서비스는 H2 인메모리 DB를 사용해 독립 실행됩니다.

```bash
# 서비스별 테스트 (Linux/macOS)
for svc in user-service wallet-service banking-service transfer-service reward-service ledger-service; do
  echo "=== $svc ===" && cd $svc
  sed -i 's/\r//' gradlew && chmod +x gradlew
  ./gradlew test --no-daemon -q
  cd ..
done

# Windows
cd user-service && .\gradlew.bat test
```

주요 테스트 클래스:

| 서비스 | 테스트 클래스 |
|---|---|
| user-service | UserServiceTest |
| wallet-service | WalletServiceTest |
| banking-service | BankingServiceTest, TossPaymentServiceTest, **BankingControllerTest** |
| transfer-service | TransferServiceTest, OutboxEventRelayTest, TransferCompensationControllerTest |
| reward-service | RewardServiceTest, **MissionControllerTest**, **FamilyControllerTest** |
| ledger-service | LedgerServiceTest, LedgerControllerTest, TransferEventConsumerTest |

### E2E 시나리오 (k6)

전체 서비스가 기동된 상태에서 실행합니다:

```bash
# 기능 검증 (1회 실행)
k6 run k6/e2e-scenario.js

# 부하 테스트 (VU 10, 30초)
k6 run --env SCENARIO=load k6/e2e-scenario.js

# 커스텀 설정
k6 run --env BASE_URL=http://localhost --env INVITE_CODE=PAYFLOW-PARENT-2024 k6/e2e-scenario.js
```

## 핵심 설계 결정

### 잔액 정합성

- 지갑 잔액의 source of truth는 `wallet-service`입니다.
- 잔액 변경은 DB 트랜잭션 안에서 처리하고 `wallet_transactions`로 모든 변경 근거를 남깁니다.
- 같은 `idempotency_key` + 같은 body는 기존 결과 반환, 같은 key + 다른 body는 `409 Conflict`.

### 멱등성 적용 범위

| 도메인 | 키 |
|---|---|
| 송금 | `transfers.idempotency_key` + `request_hash` |
| 오픈뱅킹 충전/출금 | `banking_transfers.idempotency_key` + `request_hash` |
| Toss PG 충전 | `payment_charges.idempotency_key` + `request_hash` |
| Toss 웹훅 | `toss_payment_events.event_idempotency_key` |
| 지갑 반영 | `wallet_transactions.idempotency_key` |

### 보안 계층

| 항목 | 구현 |
|---|---|
| 역할 보호 | 회원가입 시 `role` 직접 지정 불가. `inviteCode`로 PARENT/CHILD 결정 |
| 헤더 위조 방지 | 게이트웨이에서 `X-User-*`, `X-Internal-*` 헤더 제거 후 재주입 |
| 내부 시크릿 비교 | `MessageDigest.isEqual()` 상수 시간 비교 (타이밍 공격 방지) |
| OpenBanking 토큰 암호화 | PBKDF2WithHmacSHA256 (65,536회 반복) 유도 키 + 레거시 SHA-256 fallback |
| Login Rate Limiting | nginx `limit_req_zone` — 5req/min, burst=3 |
| 운영 환경 키 검증 | `prod` 프로파일에서 암호화 키 미설정 시 `@PostConstruct` 기동 실패 |

### 장애 복구 흐름

- **오픈뱅킹 충전**: `BANK_PROCESSING` → 스케줄러 지수 백오프 결과 재조회
- **오픈뱅킹 출금**: 지갑 차감 후 이체 실패 시 `COMPENSATION_REQUIRED` → `/compensate`
- **Toss PG**: 웹훅으로 최종 확정, 지갑 입금 실패 시 `COMPENSATION_REQUIRED`
- **Kafka DLT**: ledger-service consumer 3회 재시도 후 `{topic}.DLT`로 이동
- **Outbox**: ShedLock으로 단일 인스턴스만 실행, 미발행 이벤트 주기적 재발행
- **고착 송금**: `StuckTransferMonitor` — 30분 이상 `PROCESSING` 상태를 5분마다 감지·경고
- **지갑 생성 재시도**: 가입 직후 wallet-service 장애 시 `@Retryable` 3회 재시도

### 이벤트와 원장

```
transfer-service
  → outbox_events (DB)
  → OutboxEventRelay (Scheduler + ShedLock)
  → Kafka: transfer.completed / transfer.failed

ledger-service
  ← transfer.completed → ledger_entries + ledger_lines (복식부기)
  ← transfer.failed   → transfer_failure_events

banking-service
  → /ledgers/internal/payment-charge (동기 HTTP)
  → ledger_entries + ledger_lines (Toss PG 충전/취소)
```

## 주요 환경 변수

| 변수 | 설명 | 비고 |
|---|---|---|
| `JWT_SECRET` | JWT 서명 키 | 32자 이상, 운영 시 교체 필수 |
| `INTERNAL_SERVICE_SECRET` | 서비스 간 내부 인증 키 | 운영 시 교체 필수 |
| `PARENT_INVITE_CODE` | PARENT 역할 가입 초대 코드 | 운영 시 교체 필수 |
| `OPENBANKING_TOKEN_ENCRYPTION_SECRET` | 오픈뱅킹 토큰 AES 암호화 키 | prod 프로파일 필수 |
| `TOSS_SECRET_KEY` | Toss Payments 시크릿 키 | — |
| `MYSQL_PASSWORD` | MySQL 사용자 패스워드 | 운영 시 교체 필수 |
| `NGINX_PORT` | nginx 외부 포트 | 기본값 `80` |

> 운영 배포 전 `JWT_SECRET`, `INTERNAL_SERVICE_SECRET`, `PARENT_INVITE_CODE`, `MYSQL_PASSWORD`, `OPENBANKING_TOKEN_ENCRYPTION_SECRET` 5개를 반드시 강한 랜덤 값으로 교체하세요.
