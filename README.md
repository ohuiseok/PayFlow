# PayFlow

PayFlow는 MSA 기반의 청년 정책 참여 미션 및 지원금 지급 플랫폼입니다.

기존 결제/지갑 구조를 유지하면서, 기관 담당자가 청년 참여자에게 정책 미션을 등록하고 제출 내용을 승인하면 지원금이 청년 지갑으로 지급되는 흐름을 구현합니다. 지갑 잔액 정합성, 송금 멱등성, 서비스 경계, 원장 기록, 장애 보상 흐름을 함께 다루는 포트폴리오용 시스템입니다.

## 목표 흐름

```text
회원가입/로그인
-> 기관/청년 지갑 생성 (자동)
-> 기관 지원금 예산 충전 (Toss PG / 오픈뱅킹)
-> 기관-청년 참여자 연결
-> 기관이 정책 미션 등록
-> 청년이 완료 제출
-> 기관 승인
-> 기관 지갑에서 청년 지갑으로 지원금 송금
-> 지갑 거래 내역과 원장 기록 확인
```

## 서비스 구성

```text
Client
  |
Nginx (:80)
  |
API Gateway (:8080)  - JWT 검증, 사용자 헤더 주입
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
| api-gateway | 외부 요청 진입점, JWT 검증, 사용자 헤더 주입 |
| user-service | 회원가입, 로그인, JWT 발급 |
| wallet-service | 지갑 생성, 잔액 조회, 입금/출금, 거래 내역 |
| banking-service | 계좌 연결, 충전/출금, Toss PG 처리 |
| transfer-service | 지갑 간 송금, 멱등성, 보상 환불, Transactional Outbox |
| reward-service | 참여자 연결, 정책 미션 등록/제출/승인/지원금 지급, 사용 내역 조회 |
| ledger-service | 송금/충전 이벤트 기반 원장 기록 |
| settlement-service | 향후 정산 기능 확장 영역 |

## 빠른 시작

```bash
cp .env.example .env
docker compose -f docker-compose.infra.yml up -d
docker compose up -d
```

상태 확인:

```bash
curl http://localhost/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```

## 주요 API 예시

### 회원가입 / 로그인

```bash
# 기관 담당자 가입 (초대 코드 필요)
curl -X POST http://localhost/api/users \
  -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"01011112222","password":"password1234","name":"Agency","inviteCode":"PAYFLOW-PARENT-2024"}'

# 청년 참여자 가입
curl -X POST http://localhost/api/users \
  -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"01033334444","password":"password1234","name":"Youth"}'

# 로그인
curl -X POST http://localhost/api/users/login \
  -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"01011112222","password":"password1234"}'
```

### 정책 미션 지원금 흐름

```bash
# 1. 기관-청년 참여자 연결
curl -X POST http://localhost/api/families/links \
  -H 'Authorization: Bearer {AGENCY_TOKEN}' \
  -H 'Content-Type: application/json' \
  -d '{"childUserId":2}'

# 2. 정책 미션 생성
curl -X POST http://localhost/api/missions \
  -H 'Authorization: Bearer {AGENCY_TOKEN}' \
  -H 'Content-Type: application/json' \
  -d '{"childUserId":2,"title":"청년 금융 교육 참여","description":"수료 화면 제출","rewardAmount":5000}'

# 3. 정책 미션 제출
curl -X PATCH http://localhost/api/missions/1/submit \
  -H 'Authorization: Bearer {YOUTH_TOKEN}' \
  -H 'Content-Type: application/json' \
  -d '{"submissionNote":"참여 완료 증빙을 제출합니다"}'

# 4. 기관 승인
curl -X PATCH http://localhost/api/missions/1/approve \
  -H 'Authorization: Bearer {AGENCY_TOKEN}'

# 5. 지원금 지급
curl -X POST http://localhost/api/missions/1/pay \
  -H 'Authorization: Bearer {AGENCY_TOKEN}'
```

## 테스트

```bash
cd user-service && .\gradlew.bat test
```

각 서비스는 독립 Gradle 프로젝트로 구성되어 있으며, H2 기반 테스트 리소스를 사용합니다.
