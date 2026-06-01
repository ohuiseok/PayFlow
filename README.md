# PayFlow - MSA 기반 간편결제 포트폴리오

PayFlow는 P2P 송금 도메인을 기반으로 MSA 환경에서 결제 정합성, 원장 기록, 중복 요청 방지, 장애 격리, 이벤트 발행 신뢰성, 정산 흐름을 학습하고 구현하는 결제 시스템입니다.

확장 기능으로는 **용돈 캘린더**를 둡니다. 부모가 실제 보상금이 걸린 일을 등록하고, 아이가 일을 완료하면 부모 승인 후 부모 지갑에서 아이 지갑으로 용돈이 지급되며, 그 경험이 캘린더에 기록됩니다.

핵심 목표는 단순 CRUD가 아니라 **분산 환경에서 돈의 흐름을 어떻게 안전하게 기록하고 복구할 것인가**를 설계하고 검증하는 것입니다.

## 핵심 목표

- P2P 송금과 지갑 잔액 관리
- 부모 미션 기반 어린이 용돈 지급과 캘린더 기록
- 금융결제원 오픈뱅킹 테스트베드 또는 mock adapter 기반 충전/출금 경계 설계
- 원장 기반 거래 기록
- Redis 분산 락과 Idempotency Key를 활용한 중복 결제 방지
- Transactional Outbox Pattern 기반 Kafka 이벤트 발행 신뢰성 확보
- OpenFeign 기반 서비스 간 동기 통신
- Kafka 기반 결제 이벤트 후처리
- Resilience4j 기반 Circuit Breaker, Retry, Timeout
- Spring Batch 기반 정산 확장

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

| 서비스 | 역할 |
|---|---|
| api-gateway | 외부 요청 진입점, 라우팅, JWT 검증 |
| user-service | 사용자 정보, 로그인, JWT 발급 |
| wallet-service | 지갑 생성, 잔액 조회, 잔액 차감/증가, 잔액 변경 이력 |
| banking-service | 오픈뱅킹 테스트베드/mock 연동, 외부 은행망 거래 상태 관리 |
| transfer-service | 송금 요청, 송금 상태 관리, 멱등성 검증, Outbox 이벤트 저장 |
| reward-service | 부모 미션 등록, 아이 완료 요청, 보상 지급 요청, 용돈 캘린더 조회 |
| ledger-service | 거래 원장 기록, 차변/대변 기록, 불변 거래 이력 |
| settlement-service | 일별 정산, 수수료 계산, 정산 배치 |

단일 EC2와 Docker Compose 환경에서는 별도 Service Discovery 서버를 두지 않고, Docker 네트워크의 서비스 이름 DNS를 사용해 내부 서비스를 라우팅합니다.

## 핵심 결제 흐름

```text
1. Client -> API Gateway
2. API Gateway -> Banking Service: 오픈뱅킹 충전 요청
3. Banking Service -> Open Banking Testbed/Mock: 출금이체 요청
4. Banking Service -> Wallet Service: 충전 성공 후 잔액 반영
5. API Gateway -> Transfer Service: 송금 요청
6. Transfer Service: Idempotency Key 검증
7. Transfer Service -> Wallet Service: 송신자 잔액 차감
8. Transfer Service -> Wallet Service: 수신자 잔액 증가
9. Transfer Service: Outbox Event 저장
10. Outbox Publisher -> Kafka: TransferCompleted 이벤트 발행
11. Ledger Service: 이벤트를 기반으로 원장 기록
12. Settlement Service: 거래 데이터를 기반으로 일별 정산
```

## 용돈 캘린더 흐름

```text
1. 부모가 아이에게 줄 보상 미션 등록
2. 아이가 미션 완료 요청
3. 부모가 완료 요청 승인
4. Reward Service -> Transfer Service: 보상 지급 송금 요청
5. Transfer Service -> Wallet Service: 부모 지갑 차감, 아이 지갑 증가
6. Reward Service: 미션을 PAID 상태로 변경하고 transferId 저장
7. 아이 캘린더에 "무엇을 해서 얼마를 벌었는지" 기록
```

핵심 메시지:

```text
아이에게 돈은 충전되는 숫자가 아니라, 내가 만든 가치의 기록이 된다.
```

## 데모 시나리오

포트폴리오 시연은 아래 순서로 검증한다.

```text
1. 회원가입
2. 로그인 후 JWT 발급
3. 송신자/수신자 지갑 생성
4. 오픈뱅킹 mock 충전 요청
5. 충전 성공 후 송신자 지갑 잔액 확인
6. 송금 요청
7. 같은 Idempotency-Key로 송금 재요청
8. 송신자/수신자 잔액 확인
9. 송금별 원장 조회
10. Outbox 이벤트 발행 상태 확인
11. 정산 서비스 profile 실행 후 일별 정산 실행
```

공모전 시연은 아래 흐름을 추가로 검증한다.

```text
1. 부모/아이 회원가입 및 로그인
2. 부모/아이 지갑 생성
3. 부모 지갑에 용돈 재원 충전
4. 부모가 "설거지 1,000원" 미션 등록
5. 아이가 미션 완료 요청
6. 부모가 승인
7. 부모 지갑에서 아이 지갑으로 1,000원 지급
8. 아이 캘린더에 "설거지 +1,000원" 기록
9. 월별 용돈 합계 조회
```

## 결제 도메인 설계 포인트

### 1. 잔액 정합성

- 잔액의 진실은 `wallet-service`만 소유
- 잔액 검증과 차감은 하나의 DB 트랜잭션에서 처리
- 같은 지갑에 대한 동시 요청은 Redis 분산 락으로 제어
- 잔액 변경 이력을 별도 저장

### 2. 멱등성

- 모든 송금 요청은 `Idempotency-Key`를 필수로 요구
- 동일 키로 재요청이 들어오면 기존 처리 결과 반환
- 네트워크 재시도 상황에서도 중복 차감 방지
- wallet-service도 `referenceType/referenceId` 기반으로 중복 차감/입금을 방지

### 3. 송금 상태 관리

```text
REQUESTED -> PROCESSING -> COMPLETED
                         -> FAILED
                         -> COMPENSATION_REQUIRED -> ROLLED_BACK
                                                  -> ROLLBACK_FAILED
```

- 송금 단계별 상태 저장
- 실패 시 원인 기록
- 재처리 가능한 상태와 불가능한 상태 분리
- 송신자 차감 후 수신자 입금 실패는 보상 필요 상태로 남김

### 4. 오픈뱅킹 연동 경계

- 외부 은행망 요청 상태는 `banking-service`가 관리
- 오픈뱅킹 테스트베드 또는 mock adapter를 profile로 전환
- 외부 출금이체 성공이 확정된 뒤에만 `wallet-service` 충전 API 호출
- `bank_tran_id`, `api_tran_id`를 저장해 중복 요청과 응답 불명 상황 추적
- 계좌번호 원문은 저장하지 않고 마스킹 값 또는 테스트 식별자만 저장

### 5. 원장 기록

결제 회사 포트폴리오에서 중요한 포인트인 원장 관점을 반영합니다.

```text
송금 10,000원

sender wallet   -10,000
receiver wallet +10,000
```

- 거래별 차변/대변 기록
- 잔액 변경의 근거 저장
- 원장 데이터는 수정하지 않고 보정 거래로 처리
- 잔액과 원장 합계 검증 가능

### 6. Transactional Outbox Pattern

DB 저장은 성공했지만 Kafka 발행이 실패하는 상황을 방지하기 위해 Outbox 패턴을 사용합니다.

```text
Transfer DB Transaction
  +-- transfer 상태 저장
  +-- outbox_event 저장

Outbox Publisher
  +-- 미발행 이벤트 선점
  +-- Kafka 발행
  +-- 발행 완료 상태 변경
```

Outbox는 at-least-once 발행을 전제로 하며, consumer는 `eventId` 기준으로 멱등 처리합니다.

### 7. 보안과 소유권

- Gateway는 JWT를 검증하고 외부에서 들어온 `X-User-*` 헤더를 제거
- 내부 서비스는 Gateway가 새로 만든 `X-User-Id` 기준으로 소유권 확인
- 송금 요청의 sender wallet은 인증 사용자 소유 지갑이어야 함
- 내부 withdraw/deposit, 정산 실행, 복구 API는 외부 공개 제외
- Gateway는 외부 요청의 `X-Internal-*` 헤더를 제거해 내부 API spoofing을 방지
- 내부 서비스 간 잔액 반영 요청은 `X-Internal-Secret`으로 검증

### 8. 용돈 캘린더

`reward-service`는 결제 인프라를 어린이 금융 습관 서비스로 확장합니다.

- 부모가 보상 미션을 등록
- 아이가 완료 요청
- 부모 승인 후 `transfer-service`를 통해 실제 용돈 지급
- 같은 미션 승인 재시도에도 `reward-payment-{taskId}` 멱등성 키로 중복 지급 방지
- 지급 완료된 미션만 캘린더 합계에 반영
- 미션/캘린더의 진실은 `reward-service`, 잔액의 진실은 `wallet-service`, 송금의 진실은 `transfer-service`가 소유

상태 모델:

```text
REGISTERED -> SUBMITTED -> PAYMENT_PENDING -> PAID
REGISTERED -> CANCELED
SUBMITTED  -> REJECTED
```

### 9. 정산

`settlement-service`는 송금 거래와 원장 데이터를 기준으로 일별 정산을 처리합니다.

- 일별 거래 집계
- 수수료 계산
- 정산 대상 생성
- 실패 건 재처리
- Spring Batch 기반 확장

## MSA 학습 포인트

| 주제 | 구현 내용 |
|---|---|
| API Gateway | 외부 요청 라우팅, JWT 검증 |
| Internal Routing | Docker Compose 서비스 이름 기반 내부 라우팅 |
| Synchronous Communication | OpenFeign 기반 서비스 간 REST 호출 |
| Fault Tolerance | Resilience4j 기반 장애 격리 |
| Event-driven Architecture | Kafka 기반 송금 완료 이벤트 발행 |
| Event Reliability | Transactional Outbox Pattern |
| Distributed Data | 서비스별 DB 분리와 데이터 소유권 분리 |
| Product Extension | 기존 송금 인프라를 reward-service의 용돈 지급 기능으로 재사용 |

## 기술 스택

| 영역 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0.6 |
| MSA | Spring Cloud Gateway, OpenFeign, Resilience4j |
| Data | Spring Data JPA, MySQL 8.x |
| Cache/Lock | Redis 7.x |
| Messaging | Kafka |
| Batch | Spring Batch |
| Infra | Docker Compose, Nginx |

## 서비스별 데이터 소유권

물리 MySQL 인스턴스는 하나를 사용하되, 서비스별 논리 DB를 분리합니다.

| 서비스 | DB | 소유 데이터 |
|---|---|---|
| user-service | payflow_user | 사용자, 인증 정보 |
| wallet-service | payflow_wallet | 지갑, 잔액, 잔액 변경 이력 |
| banking-service | payflow_banking | 외부 은행 계좌 식별자, 오픈뱅킹 거래 상태, API 응답 추적 |
| transfer-service | payflow_transfer | 송금 요청, 송금 상태, 멱등성 요청, Outbox 이벤트 |
| reward-service | payflow_reward | 보상 미션, 완료 요청, 지급 상태, 용돈 캘린더 기록 |
| ledger-service | payflow_ledger | 원장, 차변/대변 기록, 거래 불변 이력 |
| settlement-service | payflow_settlement | 정산 집계, 수수료, 정산 결과 |

## 배포 환경

포트폴리오 시연 환경은 비용과 안정성을 고려해 단일 EC2 `t3.large`에 Docker Compose로 구성합니다.

`ledger-service`는 결제 정합성 핵심이므로 기본 실행 대상에 포함합니다. `reward-service`는 공모전 핵심 기능인 용돈 캘린더 시연 대상이며, 구현 후 기본 시연 구성에 포함합니다. `settlement-service`는 정산/배치 성격이 강하므로 필요할 때 Compose profile로 실행합니다.

| 항목 | 사양 |
|---|---|
| EC2 | t3.large |
| vCPU | 2 |
| Memory | 8GB |
| Storage | EBS gp3 30GB 이상 |
| OS | Ubuntu 22.04 LTS 또는 Ubuntu 24.04 LTS |
| 실행 방식 | Docker Compose |

### 현재 기본 실행

```text
nginx
api-gateway
user-service
wallet-service
banking-service
transfer-service
ledger-service
mysql
redis
kafka
```

### 용돈 캘린더 구현 후 시연 실행

```text
reward-service
```

### 필요 시 실행

```bash
docker compose --profile settlement up -d settlement-service
```

### 메모리 제한 기준

`t3.large`의 8GB 메모리 안에서 현재 기본 서비스를 안정적으로 실행하고, `reward-service` 구현 후에도 같은 인스턴스에서 시연할 수 있도록 컨테이너별 메모리 제한을 잡습니다. Kafka는 채용공고에서 자주 요구되는 기술이므로 유지하되, heap은 768MB로 제한합니다.

| 구성 요소 | 메모리 제한 |
|---|---:|
| nginx | 64MB |
| api-gateway | 384MB |
| user-service | 384MB |
| wallet-service | 640MB |
| banking-service | 640MB |
| transfer-service | 640MB |
| reward-service | 512MB |
| ledger-service | 384MB |
| mysql | 512MB |
| redis | 128MB |
| kafka | 1024MB |

현재 기본 실행 기준 합산 제한은 약 4.8GB입니다. `reward-service`까지 포함하면 약 5.3GB, `settlement-service`까지 함께 실행하면 약 5.7GB 수준입니다. OS, Docker daemon, page cache를 고려해도 `t3.large`의 8GB 안에서 공모전 저트래픽 시연은 가능하도록 설계합니다.

MySQL 데이터는 Docker named volume에 저장합니다.

```text
mysql_data -> payflow_mysql_data
```

따라서 컨테이너를 다시 시작하거나 인스턴스를 stop/start 해도 EBS가 유지되는 한 DB 데이터는 남습니다. 단, 아래 명령은 volume까지 삭제하므로 운영/시연 데이터가 사라질 수 있습니다.

```bash
docker compose down -v
docker volume rm payflow_mysql_data
```

이미 생성된 MySQL volume을 계속 쓰는 경우 `docker-entrypoint-initdb.d`의 초기화 스크립트는 다시 실행되지 않습니다. 기존 DB를 유지하면서 `reward-service` DB만 추가해야 한다면 MySQL에 접속해 아래 명령을 한 번 실행합니다.

```sql
CREATE DATABASE IF NOT EXISTS payflow_reward
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

GRANT ALL PRIVILEGES ON payflow_reward.* TO 'payflow'@'%';
FLUSH PRIVILEGES;
```

## API 예시

송금 요청:

```http
POST /api/transfers
Idempotency-Key: 20260530-user1-transfer-001
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "senderWalletId": 1,
  "receiverWalletId": 2,
  "amount": 10000
}
```

부모 미션 등록:

```http
POST /api/rewards/tasks
Authorization: Bearer {parent_access_token}
Content-Type: application/json

{
  "childUserId": 2,
  "parentWalletId": 1,
  "childWalletId": 2,
  "title": "설거지",
  "description": "저녁 설거지하기",
  "rewardAmount": 1000,
  "taskDate": "2026-06-03"
}
```

아이 완료 요청:

```http
POST /api/rewards/tasks/100/submit
Authorization: Bearer {child_access_token}
```

부모 승인 및 지급:

```http
POST /api/rewards/tasks/100/approve
Authorization: Bearer {parent_access_token}
Idempotency-Key: reward-task-100-approve
```

캘린더 조회:

```http
GET /api/rewards/calendar?childUserId=2&year=2026&month=6
Authorization: Bearer {access_token}
```

## 실행 방법

```bash
# 환경 변수 파일 생성
cp .env.example .env

# 로컬 개발용 인프라만 실행
docker compose -f docker-compose.infra.yml up -d

# 전체 기본 서비스 실행
docker compose up -d

# 정산 서비스까지 실행
docker compose --profile settlement up -d
```

`.env`는 로컬 실행용 파일이며 Git에 커밋하지 않습니다. 배포 환경에서는 GitHub Actions Secrets 값을 사용해 EC2에 `.env`를 생성한 뒤 `docker compose up -d`로 실행합니다.

## 테스트 목표

| 테스트 | 검증 내용 |
|---|---|
| 중복 요청 테스트 | 동일 Idempotency Key 요청 시 한 번만 차감 |
| 동시 송금 테스트 | 같은 지갑의 동시 송금 요청에서 잔액 정합성 보장 |
| 장애 테스트 | wallet-service 장애 시 transfer-service 실패 상태 기록 |
| 보상 지급 테스트 | 부모 승인 재시도 시 아이 지갑에 한 번만 지급 |
| 캘린더 테스트 | 지급 완료된 미션만 월별 합계에 반영 |
| Outbox 테스트 | Kafka 발행 실패 시 outbox_event에 남고 재발행 |
| 원장 테스트 | 송금 성공 시 차변/대변 원장 기록 생성 |
| 정산 테스트 | 일별 거래 집계와 수수료 계산 검증 |

## 제외 범위

초기 포트폴리오에서는 아래 기능을 구현하지 않습니다.

```text
운영 은행망 실거래
실제 PG/VAN 연동
KYC 상세 심사
Refresh Token rotation
복잡한 관리자 권한
복잡한 가족 권한/가족 초대 시스템
사진/영상 인증 저장소
반복 미션 자동 생성
아이 전용 카드 발급
1일 송금 한도
Kubernetes
상시 Prometheus/Grafana 운영
```

## 프로젝트 구조

```text
payflow-msa/
  api-gateway/
  user-service/
  wallet-service/
  transfer-service/
  reward-service/
  banking-service/
  ledger-service/
  settlement-service/
  infrastructure/
    mysql/
    redis/
    kafka/
    nginx/
  docker-compose.yml
  docker-compose.infra.yml
```

## 포트폴리오 어필 포인트

- 결제 도메인의 잔액 정합성을 MSA 구조에서 설계
- 오픈뱅킹 테스트베드/mock adapter로 외부 은행망과 내부 지갑 잔액 경계 분리
- Redis 분산 락과 멱등성 키를 통한 중복 결제 방지
- 부모 미션 승인 재시도에도 실제 용돈이 한 번만 지급되는 멱등 보상 흐름 설계
- "돈이 생기는 이유"를 캘린더로 보여주는 어린이 금융 접근성 서비스 확장
- 원장 기반 거래 기록 설계
- Transactional Outbox Pattern으로 이벤트 발행 신뢰성 확보
- Kafka, OpenFeign, Resilience4j를 활용한 MSA 핵심 패턴 구현
- 정산 배치와 재처리 흐름까지 고려
