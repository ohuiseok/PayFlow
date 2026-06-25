# Retry Storm

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

## 핵심 개념

Retry Storm은 장애 상황에서 많은 클라이언트나 서비스가 동시에 재시도하면서 오히려 장애를 악화시키는 현상이다. 일시 장애를 복구하려던 재시도가 트래픽 폭증을 만든다.

## PayFlow 연결

`wallet-service`가 느려졌을 때 `transfer-service` 인스턴스들이 즉시 여러 번 재시도하면 `wallet-service`와 DB는 더 큰 부하를 받는다. 사용자의 앱도 동시에 재시도하면 문제가 커진다.

## 실무 포인트

- 재시도 횟수를 제한한다.
- Exponential Backoff와 Jitter를 사용한다.
- 모든 실패를 재시도하지 않는다.
- Circuit Breaker와 Rate Limit을 함께 사용한다.
- 결제 명령은 멱등성 키 없이 재시도하지 않는다.

## 체크 질문

- Retry Storm은 왜 발생하는가
- Jitter가 필요한 이유는 무엇인가
- 결제 API에서 무조건 재시도가 위험한 이유는 무엇인가

## 실무 설계 참고

### 대표 장애 시나리오

장애가 난 wallet-service에 모든 인스턴스가 동시에 재시도해 더 큰 부하를 만든다.

### 잘못된 구현 예시

~~~text
실패하면 즉시 재시도하는 것이 사용자에게 좋다고 생각한다.
~~~

### 좋은 구현 예시

~~~text
exponential backoff, jitter, retry budget, circuit breaker를 둔다.
~~~

### 대안과 선택 이유

장애가 나면 사람이 재시작하거나 배포를 되돌리는 방식도 가능하지만, 결제 시스템에서는 장애 전파 속도가 빠르고 수동 대응은 늦다. PayFlow는 Timeout, Retry 제한, Circuit Breaker, Bulkhead, Graceful Shutdown, 호환 배포를 통해 피해 범위를 자동으로 줄이는 편이 낫다.

### PayFlow에서 확인할 위치

Resilience4j retry config, service metrics, access logs

### 면접에서 설명하기

재시도는 약이다. 용량과 간격을 정하지 않으면 장애를 증폭시키는 독이 된다.

### 관련 문서

33, 66, 67

## 심화 참고

### 재시도해도 되는 실패와 안 되는 실패

모든 실패를 재시도하면 안 된다. 재시도는 일시 장애에만 효과가 있고, 영구 실패나 검증 실패에는 부하만 늘린다.

| 실패 유형 | 예시 | 재시도 여부 |
| --- | --- | --- |
| 일시 장애 | 네트워크 타임아웃, 503, 커넥션 일시 부족 | 제한적으로 재시도 |
| 과부하 | 429, circuit open, thread pool 포화 | 즉시 재시도 금지, backoff 필요 |
| 검증 실패 | 잔액 부족, 잘못된 금액, 권한 없음 | 재시도 금지 |
| 상태 충돌 | 중복 요청, 이미 처리됨 | 멱등성 결과 반환 |
| 알 수 없는 실패 | 응답 전 연결 끊김 | 멱등성 키 기반 조회 후 판단 |

결제 명령은 특히 "서버가 처리했는지 모르겠다"는 상태가 많다. 이때 무작정 재시도하면 중복 출금이 될 수 있으므로 먼저 멱등성 키나 거래 상태 조회로 기존 처리 결과를 확인해야 한다.

### Retry Budget

Retry Budget은 전체 요청 중 재시도가 차지할 수 있는 비율을 제한하는 방식이다. 예를 들어 정상 요청 1000건 중 재시도는 최대 100건까지만 허용한다. 이렇게 하면 장애 상황에서 재시도가 원래 트래픽보다 커지는 상황을 막을 수 있다.

```text
정상 트래픽: 1000 RPS
허용 재시도: 최대 10%
재시도 예산: 100 RPS

재시도 예산 초과 시:
  - 즉시 실패 반환
  - circuit breaker open
  - queue 적재 제한
  - 사용자에게 처리 중 상태 제공
```

### PayFlow 적용 예시

```text
transfer-service -> wallet-service 호출

timeout: 300ms
retry:
  maxAttempts: 2
  backoff: 100ms, 300ms
  jitter: enabled
retry target:
  IOException, TimeoutException, 503
no retry:
  400, 401, 403, 409, insufficient_balance
circuit breaker:
  failureRateThreshold: 50%
  waitDurationInOpenState: 10s
```

이 설정의 핵심은 빠른 성공이 아니라 장애 전파 차단이다. `wallet-service`가 이미 느린데 `transfer-service`가 긴 타임아웃과 많은 재시도를 잡으면 스레드가 묶이고 API Gateway까지 응답 지연이 번진다.

### 운영 지표

- 원 요청 수 대비 재시도 수 비율
- 재시도 후 성공률
- 재시도 때문에 증가한 p95/p99 latency
- circuit breaker open 횟수와 지속 시간
- timeout 발생 서비스와 downstream 서비스의 CPU, DB connection, thread pool 사용률
- 같은 idempotency key의 재요청 수

### 테스트 포인트

- `wallet-service` 응답을 느리게 만들었을 때 재시도가 제한되는지 확인한다.
- 여러 인스턴스가 동시에 실패를 만나도 jitter로 재시도 시점이 분산되는지 확인한다.
- 4xx 검증 실패에 대해 재시도하지 않는지 확인한다.
- 멱등성 키가 없는 결제 명령 재시도를 거부하는지 확인한다.

## 질문의 질문으로 더 깊게 보기

### 이 문서에서 잡아야 할 CS 감각

이 구간의 핵심은 장애를 예외 상황이 아니라 설계 입력으로 보는 것이다. Timeout, Retry, Circuit Breaker, Graceful Shutdown, 배포 전략은 모두 장애가 났을 때 피해를 제한하고 복구 가능하게 만드는 장치다.

### 질문의 질문

아래 질문들은 답을 외우기 위한 질문이 아니다. 하나씩 답하면서 "왜 그런 설계가 필요한지"를 설명하는 연습을 하기 위한 질문이다.

- Retry Storm 개념이 없다면 PayFlow에서 가장 먼저 어떤 장애가 생기는가?
- 이 개념은 정확성, 성능, 보안, 운영성 중 무엇을 가장 크게 개선하는가?
- 반대로 이 개념을 잘못 적용하면 어떤 복잡도가 추가되는가?
- 이 개념을 적용하면 어떤 문제가 해결되지만, 어떤 비용이나 복잡도가 새로 생기는가?
- 장애가 발생했을 때 이 개념은 문제를 예방하는가, 감지하는가, 복구하는가?
- PayFlow 코드에서 이 개념을 확인하려면 어떤 서비스, 테이블, 로그, 테스트를 봐야 하는가?

### 답안 예시

<details>
<summary>답안 예시 펼치기</summary>

#### 핵심 답변

Retry Storm 개념은 PayFlow에서 다음 이유로 중요하다.

- 장애 대응과 배포 개념은 PayFlow가 실패를 완전히 피하지 못하더라도 피해를 제한하고 안전하게 복구하기 위해 필요하다.
- 서비스 상태, 배포 버전, DB 마이그레이션 이력, health check, retry/circuit breaker 상태가 기준이다.
- 무제한 재시도, 타임아웃 부재, 배포 중 스키마 불일치, 종료 중 메시지 처리 중단이 장애를 확대한다.
- Timeout, Retry Backoff, Circuit Breaker, Bulkhead, Graceful Shutdown, 하위 호환 배포, Readiness로 방어한다.
- 운영에서는 timeout 수, retry 수, circuit open 수, readiness 실패, 배포 후 에러율, Consumer rebalance를 본다.

#### 질문에 답하는 방식

좋은 답변은 용어 정의에서 멈추지 않고 다음 순서로 이어져야 한다.

1. 어떤 데이터나 요청을 보호하려는 개념인지 말한다.
2. 그 데이터의 진실의 원천이 어느 서비스에 있는지 말한다.
3. 장애, 중복, 동시성, 지연 중 어떤 상황에서 문제가 생기는지 설명한다.
4. 코드 레벨의 방어 수단과 운영 레벨의 확인 수단을 함께 말한다.

#### PayFlow 예시 답변

```text
Retry Storm 개념은 PayFlow에서 장애 상황의 재시도가 장애를 더 키우지 않게 하기 위해 필요하다.
이 개념이 없으면 wallet-service가 느린데 모든 transfer-service 인스턴스가 즉시 재시도해 DB 부하가 폭증할 수 있다.
그래서 코드에서는 exponential backoff, jitter, retry budget, circuit breaker를 적용하고,
운영에서는 재시도 횟수, retry burst, circuit open 전후 트래픽을 확인해야 한다.
```

#### 더 생각해볼 점

이 답안은 하나의 예시다. 실제 설계에서는 성능, 복잡도, 장애 복구 난이도 사이의 trade-off를 함께 봐야 한다. 특히 PayFlow처럼 돈을 다루는 시스템에서는 빠른 성공보다 명확한 실패, 추적 가능한 상태, 재처리 가능한 구조가 더 중요하다.

</details>

### PayFlow에 대입해보기

1. 이 개념이 가장 직접적으로 연결되는 PayFlow 서비스 하나를 고른다.
2. 그 서비스가 소유한 데이터의 "진실의 원천"이 무엇인지 적어본다.
3. 동시에 두 요청이 들어오거나, 네트워크가 끊기거나, 프로세스가 죽는 상황을 가정한다.
4. 그 상황에서 데이터가 깨지지 않으려면 어떤 제약조건, 트랜잭션, 락, 이벤트, 재처리 장치가 필요한지 설명한다.
5. 마지막으로 운영자가 문제를 발견할 수 있는 로그나 지표가 무엇인지 적어본다.

### 설명 연습

다음 문장을 자기 말로 완성해보자.

```text
Retry Storm 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.

