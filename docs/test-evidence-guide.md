# 송금 테스트 증적 실행 가이드

성능 요청은 k6, 데이터 정합성은 MySQL SQL, 보상과 Outbox 동작은 JUnit으로 검증한다. 모든 결과는 동일한 실행 ID 아래에 저장된다.

## 1. 준비

전체 서비스를 실행한다.

```powershell
docker compose up -d --build
```

테스트 사용자 파일을 만든다.

```powershell
Copy-Item k6\test-users.example.json k6\test-users.local.json
```

`k6/test-users.local.json`에 실제 송신자 JWT와 수신자 ID를 입력한다. 이 파일은 Git에서 제외된다. 처리량 테스트에서는 한 지갑의 락 경합이 전체 성능을 제한하지 않도록 충분한 잔액을 가진 송신자를 여러 명 등록한다.

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

동시 송금 1,000건:

```powershell
.\scripts\run-test-evidence.ps1 -Mode concurrent -Vus 1000 -Duration 2m
```

420 TPS 처리량 테스트:

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

전체 지갑 잔액 비교는 테스트 도중 다른 요청이 없는 격리 환경을 전제로 한다. 송금 API는 HTTP 201이어도 본문 상태가 `FAILED`일 수 있으므로 k6 결과의 `businessSucceeded`와 `achievedBusinessTps`를 성능 근거로 사용한다.
