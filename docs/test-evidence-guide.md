# 송금 테스트 증적 실행 가이드

성능 요청은 k6, 데이터 정합성은 MySQL SQL, 보상과 Outbox 동작은 JUnit으로 검증한다. 모든 결과는 동일한 실행 ID 아래에 저장된다.

## 1. 준비

전체 서비스를 실행한다.

```powershell
docker compose up -d --build
```

테스트 사용자 파일을 만든다. 명령을 실행하면 전화번호, 수신자 ID, 비밀번호를 차례로 묻고 로그인 JWT를 Git에서 제외된 로컬 파일에 저장한다.

```powershell
.\scripts\prepare-test-users.ps1
```

직접 편집하려면 `k6/test-users.example.json`을 `k6/test-users.local.json`으로 복사한 뒤 실제 송신자 JWT와 수신자 ID를 입력한다. 이 파일은 Git에서 제외된다. 처리량 테스트에서는 한 지갑의 락 경합이 전체 성능을 제한하지 않도록 충분한 잔액을 가진 송신자를 여러 명 등록한다.

```json
{
  "amount": 1000,
  "users": [
    { "senderUserId": 1, "receiverUserId": 2, "token": "JWT" },
    { "senderUserId": 3, "receiverUserId": 4, "token": "JWT" }
  ]
}
```

로컬에 k6가 없으면 실행 스크립트가 Docker의 `grafana/k6:latest`를 사용한다.

## 2. 실행

### EC2 전체 자동 실행

EC2에 Docker Compose로 배포된 환경은 다음 명령 하나로 실행한다.

로컬 `.env`에 SSH 접속 정보를 한 번 설정한다. PEM 파일 자체를 프로젝트에 복사하거나 Git에 커밋하면 안 된다.

```dotenv
EC2_HOST=EC2_PUBLIC_IP_OR_HOST
EC2_SSH_USER=ubuntu
EC2_SSH_KEY_PATH=C:\keys\payflow.pem
```

```powershell
.\scripts\run-ec2-test-evidence.ps1
```

접속 설정의 우선순위는 프로세스 환경변수/로컬 `.env`, `scripts/test-evidence.ec2.local.json` 순서다. 값이 없을 때만 첫 실행에서 입력을 요청한다.

1. SSH 터널과 EC2 Docker 상태 확인
2. EC2 내부 API Gateway와 Docker 상태 확인
3. 저장된 JWT 확인, 계정이 없으면 합성 송신자·수신자 자동 가입
4. EC2 내부 `wallet-service`로 합성 계정 테스트 크레딧 적립
5. EC2 MySQL 테스트 전 잔액 기준선 저장
6. 외부 도메인을 대상으로 k6 실행
7. EC2 MySQL 잔액·Outbox·원장 검증
8. 로컬 JUnit 실행과 결과 통합

합성 계정의 자격 증명과 JWT는 Git에서 제외되는 `k6/test-accounts.local.json`, `k6/test-users.local.json`에만 저장된다. 첫 실행 뒤 부하 조건을 바꾸려면 `scripts/test-evidence.ec2.local.json`의 `mode`, `vus`, `rate`, `duration`, `senderCount`를 수정한다.

테스트 크레딧은 OpenBanking을 호출하지 않는다. EC2의 `wallet-service` 컨테이너 안에서 기존 `INTERNAL_SECRET`을 사용해 적립하며, `MANUAL_CHARGE`와 `evidence-<실행 ID>-*` 참조값으로 지갑 거래 내역을 남긴다. 내부 비밀값은 EC2 밖으로 출력하거나 결과 파일에 저장하지 않는다. 적립이 끝난 뒤 SQL 기준선을 저장하므로 송금 전후 총액 검증에는 테스트 크레딧이 섞이지 않는다.

### 개별 실행

동시 송금 1,000건:

```powershell
.\scripts\run-test-evidence.ps1 -Mode concurrent -Vus 1000 -Duration 2m
```

420 TPS 목표 처리량 테스트(목표 부하 설정이며, 실행 결과의 실측 TPS와 구분):

```powershell
.\scripts\run-test-evidence.ps1 -Mode throughput -Rate 420 -Duration 5m
```

동일 송신 지갑 Hot Wallet 테스트:

```powershell
.\scripts\run-test-evidence.ps1 -Mode hot-wallet -Vus 1000
```

동일 멱등성 키 100회 테스트:

```powershell
.\scripts\run-test-evidence.ps1 -Mode idempotency -Vus 100
```

Kafka 중단과 Outbox 복구 테스트:

```powershell
.\scripts\run-test-evidence.ps1 -Mode concurrent -Vus 100 -KafkaOutage -OutboxRecoveryWaitSeconds 30
```

Kafka를 오래 중단하면 기본 Outbox 재시도 횟수 5회를 소진할 수 있다. 장애 복구 테스트에서는 재시도 횟수를 충분히 높이거나 요청 직후 Kafka를 복구한다.

JUnit만 다시 실행하려면 다음 명령을 사용한다.

```powershell
.\scripts\run-junit-evidence.ps1 -ResultDir results\junit-manual
```

## 3. 결과 확인

실행 결과는 `results/<실행 ID>/`에 저장된다.

```text
results/<실행 ID>/
├─ evidence-summary.json       # k6, SQL, JUnit 통합 판정
├─ evidence-summary.log
├─ run-metadata.json           # Git SHA와 테스트 조건
├─ k6-console.log
├─ k6-summary.json             # 성공 TPS와 응답시간
├─ k6-raw-summary.json
├─ sql-before.json             # 테스트 전 전체 지갑 잔액
├─ sql-before.log
├─ sql-summary.json            # 잔액·중복·Outbox·원장 자동 판정
├─ sql-after.log               # 실제 SQL 조회 표
├─ wallet-seed-summary.json    # 합성 계정별 테스트 크레딧 적립 결과
└─ junit/
   ├─ junit-summary.json
   ├─ transfer-service.log
   ├─ ledger-service.log
   ├─ transfer-service-html/index.html
   └─ ledger-service-html/index.html
```

주요 판정 기준은 다음과 같다.

- k6: HTTP 오류, 비정상 응답, dropped iteration이 없어야 한다.
- SQL: 전체 잔액 증감, 중복 지갑 거래, 성공 송금의 출금·입금 누락, Outbox 미발행, 성공 원장 누락이 모두 0이어야 한다.
- JUnit: transfer-service와 ledger-service 테스트의 failure와 error가 모두 0이어야 한다.

현재 증적 스크립트의 통합 판정에는 settlement-service가 포함되지 않는다. 정산 회귀는 별도로 실행한다.

```powershell
cd settlement-service
.\gradlew.bat test --rerun-tasks
```

이 테스트는 이벤트 중복 수집, 승인/취소 집계, 수수료 계산, 원장 대사와 완료 기준일 재호출을 검증하지만 실제 Kafka broker 경로는 검증하지 않는다.

전체 지갑 잔액 비교는 테스트 도중 다른 요청이 없는 격리 환경을 전제로 한다. 송금 API는 HTTP 201이어도 본문 상태가 `FAILED`일 수 있으므로 k6 결과의 `businessSucceeded`와 `achievedBusinessTps`를 성능 근거로 사용한다.

## 4. 최신 검증 결과

블로그·포트폴리오에는 설정값이나 목표치가 아니라 아래 통합 판정 통과 결과를 사용한다.

- 실행 ID: `20260629-214652`
- 대상: `https://app.pay-flow.cloud`
- Git SHA: `011e4b93f160781ff04f108fa040f972b356c818`
- 조건: `1,000 VU`, 송금 `1,000건`, 송신자 200명, 수신자 200명, 건당 1,000원
- 결과: 업무 성공 1,000건, 업무 실패 0건, 비정상 응답 0건, dropped iteration 0건
- 처리량: 실측 `28.20 TPS`, 전체 실행 시간 `35.46초`
- 응답시간: 평균 `20,470.26ms`, p95 `31,285.92ms`, p99 `31,845.22ms`
- 잔액 정합성: 테스트 전·후 총액 7,230,000원, 증감 0원, 음수 잔액 0건
- 중복·누락: 중복 멱등키 0건, 중복 지갑 거래 0건, 성공 송금 거래 이상 0건
- Outbox·원장: 누락 Outbox 0건, 미발행 Outbox 0건, 성공 송금 원장 누락 0건, 중복 원장 0건
- 회귀 테스트: transfer-service와 ledger-service JUnit 41건 통과, 실패·오류·skip 0건

근거 파일:

```text
results/20260629-214652/run-metadata.json
results/20260629-214652/k6-summary.json
results/20260629-214652/sql-summary.json
results/20260629-214652/junit/junit-summary.json
results/20260629-214652/evidence-summary.json
```

현재 저장된 증적에는 변경 전·후 성능을 동일 조건으로 비교한 결과와 420 TPS 처리량 모드의 완료 결과가 없다. 따라서 `50 → 420 TPS`, `1.2s → 140ms`, `8.4배`, `88% 개선`은 성과로 사용하지 않는다. Kafka 장애 주입 통합 실행 결과도 없으므로 “이벤트 유실 완전 방지” 대신 Outbox SQL 검증과 relay 단위 테스트가 통과했다고 표현한다.

### 블로그용 주요 성과

- 1,000 VU 동시 송금 1,000건을 실행해 업무 성공 1,000건, 실패·비정상 응답·dropped iteration 0건을 확인했다.
- 실측 처리량은 28.20 TPS였으며 평균 응답시간은 20.47초, p95는 31.29초로 측정돼 성능 병목을 수치로 확인했다.
- 테스트 전·후 전체 지갑 잔액 증감 0원, 음수 잔액·중복 지갑 거래·성공 송금 거래 이상 각각 0건으로 정합성을 검증했다.
- 송금 1,000건에서 누락·미발행 Outbox와 성공 송금 원장 누락이 각각 0건이었고, Kafka 발행 실패 및 오래된 `PROCESSING` 이벤트 복구를 포함한 Outbox 회귀 테스트가 통과했다.
- transfer-service와 ledger-service 회귀 테스트 41건이 모두 통과했다.
