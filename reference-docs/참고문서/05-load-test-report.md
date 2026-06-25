# Load Test Report

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

이 문서는 포트폴리오용 부하 테스트 보고서 템플릿입니다. 현재 저장소에는 k6 스크립트 결과 파일이 아직 없으므로, 실제 실행 후 `Result` 섹션의 수치를 채우면 됩니다.

## Test Goal

PayFlow의 부하 테스트 목표는 단순 최대 TPS 측정보다 아래 질문에 답하는 것입니다.

- 로그인/지갑 조회/미션 조회 같은 읽기 요청은 안정적으로 처리되는가?
- 충전/송금/보상 지급처럼 잔액을 변경하는 요청에서 실패율이 증가하지 않는가?
- 멱등 요청을 반복해도 잔액이 중복 반영되지 않는가?
- 송금 완료 후 outbox 이벤트가 지연 없이 Kafka로 발행되는가?
- 병목이 API Gateway, wallet-service, transfer-service, MySQL, Redis 중 어디서 먼저 나타나는가?

## Environment

| Item | Value |
| --- | --- |
| Date | 2026-06-20 |
| Machine | Local development machine |
| Runtime | Docker Compose |
| Database | MySQL 8.4 |
| Cache/Lock | Redis 7 |
| Messaging | Apache Kafka 3.8 |
| Entry | Nginx/API Gateway |

## Target APIs

| Scenario | APIs |
| --- | --- |
| Auth | `POST /api/users/login`, `GET /api/users/me` |
| Wallet Read | `GET /api/wallets/users/{userId}` |
| Bank Deposit | `POST /api/bank/deposits`, `GET /api/bank/transfers/{id}` |
| Transfer | `POST /api/transfers`, `GET /api/transfers/{id}` |
| Mission Reward | `PATCH /api/missions/{id}/approve`, `POST /api/missions/{id}/pay` |
| Outbox Monitor | `GET /api/transfers/outbox/summary` |

## Suggested k6 Scenario

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    smoke: {
      executor: 'constant-vus',
      vus: 5,
      duration: '1m',
    },
    normal_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 20 },
        { duration: '5m', target: 20 },
        { duration: '2m', target: 0 },
      ],
    },
    spike: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 50 },
        { duration: '1m', target: 50 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api';

export default function () {
  const login = http.post(`${BASE_URL}/users/login`, JSON.stringify({
    email: __ENV.TEST_EMAIL,
    password: __ENV.TEST_PASSWORD,
  }), {
    headers: { 'Content-Type': 'application/json' },
  });

  check(login, {
    'login ok': (res) => res.status === 200,
  });

  const token = login.json('accessToken');
  const headers = {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  };

  const me = http.get(`${BASE_URL}/users/me`, { headers });
  check(me, { 'me ok': (res) => res.status === 200 });

  sleep(1);
}
```

## Test Plan

### 1. Smoke Load

목적:

- 로컬 compose 환경에서 기본 인증/조회 요청이 정상 처리되는지 확인
- 테스트 데이터와 토큰 발급 흐름 검증

기준:

| Metric | Target |
| --- | --- |
| Error rate | `< 1%` |
| p95 latency | `< 500ms` |
| p99 latency | `< 1000ms` |

### 2. Transfer Load

목적:

- 동시 송금 요청에서 wallet-service 잔액 정합성 유지 확인
- Redis lock 대기 시간이 latency에 미치는 영향 확인
- 같은 idempotency key 반복 요청이 중복 차감되지 않는지 확인

확인 쿼리/지표:

```text
transfers.status distribution
wallet_transactions count by reference_type/reference_id
outbox_events status distribution
oldestPendingEventAgeSeconds
```

### 3. Reward Payment Load

목적:

- reward-service가 정책 미션 지원금 지급 요청을 transfer-service에 안정적으로 위임하는지 확인
- `reward-payment-{missionId}` 멱등키가 중복 지급을 막는지 확인

확인 지표:

```text
reward_tasks status
paid_transfer_id uniqueness
transfer duplicate conflict count
wallet duplicate reference count
```

### 4. Outbox/Kafka Load

목적:

- transfer-service outbox relay가 PENDING 이벤트를 Kafka로 정상 발행하는지 확인
- ledger-service가 중복 이벤트를 중복 원장으로 저장하지 않는지 확인

확인 API:

```http
GET /api/transfers/outbox/summary
GET /api/ledgers/transfer-failures
```

## Result

실제 실행 후 아래 표를 채웁니다.

| Scenario | VUs | Duration | RPS | Error Rate | p95 | p99 | Notes |
| --- | ---: | --- | ---: | ---: | ---: | ---: | --- |
| Smoke | 5 | 1m | TBD | TBD | TBD | TBD | TBD |
| Normal | 20 | 9m | TBD | TBD | TBD | TBD | TBD |
| Spike | 50 | 2m | TBD | TBD | TBD | TBD | TBD |
| Transfer | TBD | TBD | TBD | TBD | TBD | TBD | TBD |

## Bottleneck Analysis Template

| Symptom | Possible Cause | Check |
| --- | --- | --- |
| p95 급증 | Redis lock 경합 | 같은 sender wallet 집중 여부 |
| 송금 실패 증가 | wallet-service timeout | wallet-service 로그와 Feign timeout |
| outbox PENDING 누적 | Kafka publish 지연 | `/api/transfers/outbox/summary` |
| ledger 누락 | consumer lag 또는 중복 방어 오류 | ledger-service consumer log |
| DB CPU 증가 | idempotency/outbox 인덱스 부족 | MySQL slow query, EXPLAIN |

## Portfolio Summary

부하 테스트에서 강조할 메시지:

- 결제성 API는 평균 응답 시간보다 실패율과 중복 반영 여부가 더 중요합니다.
- 송금 처리량이 증가할수록 같은 지갑에 대한 lock 경합이 latency를 올릴 수 있습니다.
- outbox summary API를 둔 이유는 Kafka 발행 지연을 운영 관점에서 관찰하기 위해서입니다.
- 부하 테스트 결과는 단순 TPS 숫자가 아니라 병목 위치와 개선 계획까지 함께 제시해야 설득력이 있습니다.

