# Backpressure

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

## 핵심 개념

### Backpressure의 물리적 원리

생산 속도(Produce Rate)가 소비 속도(Consume Rate)를 초과하면 버퍼가 채워진다. 버퍼가 가득 차면 두 가지 중 하나가 발생한다. 새 데이터를 버리거나(drop), 생산자가 멈춰서 기다리거나(block). Backpressure는 이 불균형을 제어하는 메커니즘이다.

TCP에서도 Backpressure가 내장되어 있다. 수신 버퍼가 가득 차면 TCP 윈도우 크기를 0으로 광고해서 송신자가 전송을 멈추게 한다. 이것이 TCP flow control이고, 메시지 시스템에서도 동일한 개념이 필요하다.

### Kafka에서의 Backpressure: Consumer Lag

Kafka는 브로커가 메시지를 보존하기 때문에 Consumer가 처리하지 못해도 메시지를 바로 버리지 않는다. 대신 Consumer Lag(Consumer와 Partition 마지막 오프셋의 차이)가 쌓인다.

```
Partition 상태:
최신 오프셋: 10000
Consumer 오프셋: 8000
Consumer Lag: 2000 (아직 처리 못한 메시지)
```

Lag이 계속 증가하면 두 가지 문제가 생긴다.

**지연 증가**: 이벤트가 발행된 후 처리될 때까지 시간이 길어진다. 송금 완료 후 원장 반영이 몇 분, 몇 시간 지연될 수 있다.

**버퍼 공간 소진**: Kafka 메시지 보존 기간(기본 7일) 동안 처리하지 못하면 메시지가 만료된다. 이는 사실상 유실과 같다.

```
# Kafka Consumer Lag 모니터링
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group ledger-consumer-group
```

### Consumer 처리량 병목 분석

Lag이 증가하는 이유를 계층별로 분석해야 한다.

**Consumer 자체 병목**
- 처리 로직이 느림 (복잡한 비즈니스 규칙)
- Consumer 인스턴스 수 부족
- Consumer 스레드 설정 문제

**DB 병목** (가장 흔한 원인)
- DB 커넥션 풀 고갈
- 느린 쿼리 (인덱스 없는 조회, 대규모 테이블 락)
- DB CPU/IO 한계

**외부 서비스 병목**
- 외부 API 응답 지연
- 외부 서비스 rate limit

```
Consumer 처리량 공식:
처리량 = Consumer 수 × 스레드 수 × (1 / 처리 시간)

Consumer 10개 × 스레드 1개 × (1 / 100ms) = 초당 100건
DB가 초당 50건 처리하면 → Lag 계속 증가
```

Consumer 수만 늘리면 안 되는 이유가 여기 있다. 병목이 DB라면 Consumer를 늘릴수록 DB에 더 많은 동시 부하가 발생해 오히려 전체 처리량이 감소할 수 있다.

### API 레벨의 Backpressure

Kafka 외에도 API 서버 간 호출에서도 Backpressure가 필요하다.

**Rate Limiting**: 단위 시간당 처리할 수 있는 요청 수를 제한한다.

```java
// Guava RateLimiter: 초당 100건 제한
RateLimiter rateLimiter = RateLimiter.create(100.0);

public void processRequest(Request request) {
    if (!rateLimiter.tryAcquire()) {
        throw new RateLimitExceededException("Too many requests");
    }
    // 처리
}
```

**큐 깊이 제한**: 내부 작업 큐가 가득 차면 새 요청을 거부한다.

```java
// ThreadPoolExecutor의 큐 크기 제한
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    10,          // corePoolSize
    20,          // maxPoolSize
    60, SECONDS, // keepAliveTime
    new ArrayBlockingQueue<>(100),  // 최대 100개 대기
    new CallerRunsPolicy()          // 가득 차면 호출자 스레드에서 직접 실행 (자연스러운 Backpressure)
);
```

**`CallerRunsPolicy`**는 특히 유용하다. 큐가 가득 차면 새 태스크를 요청한 스레드가 직접 실행하게 되어, 자연스럽게 요청 속도가 감소한다.

### Reactive Streams와 Backpressure

Project Reactor나 RxJava 같은 Reactive 프레임워크는 Backpressure를 프로토콜 레벨에서 지원한다. Subscriber가 처리할 수 있는 양을 `request(n)`으로 Publisher에게 알리고, Publisher는 그만큼만 데이터를 발행한다.

```java
// Project Reactor Backpressure 예시
Flux.range(1, 1000)
    .onBackpressureBuffer(50)   // 50개까지 버퍼
    .publishOn(Schedulers.boundedElastic())
    .subscribe(item -> {
        processSlowly(item);
    });
```

### 흔한 오해와 함정

**"Consumer를 늘리면 Lag이 줄어든다"**: Partition 수보다 Consumer 수가 많으면 초과 Consumer는 놀게 된다. Kafka는 한 Partition을 하나의 Consumer에만 할당하기 때문이다. Partition 수 이상으로 Consumer를 늘리려면 Partition도 먼저 늘려야 한다.

**"Rate Limit은 클라이언트에 에러를 주는 것이다"**: Rate Limit의 올바른 동작은 429(Too Many Requests)를 반환하고 `Retry-After` 헤더로 재시도 시점을 알리는 것이다. 클라이언트가 이에 맞게 재시도하면 시스템 전체가 안정적으로 동작한다.

**"Backpressure는 성능 저하를 유발한다"**: Backpressure 없이 처리 한계를 넘기면 전체 시스템이 OOM이나 DB 커넥션 고갈로 다운된다. Backpressure는 일부 요청을 거부하는 대신 시스템이 살아있게 유지하는 장치다.

## PayFlow 연결

송금 이벤트가 급증했는데 `ledger-service`가 처리하지 못하면 Kafka lag이 증가한다. 이때 무리하게 Consumer 수만 늘리면 DB가 병목이 될 수 있다.

API 요청도 마찬가지다. `wallet-service`가 느린데 `transfer-service`가 계속 요청을 밀어 넣으면 장애가 확대된다.

## 실무 포인트

- 큐 적체와 Consumer lag을 모니터링한다.
- Rate Limit으로 유입량을 제한한다.
- Consumer 처리량과 DB 처리량을 함께 본다.
- Timeout과 Circuit Breaker로 장애 전파를 줄인다.
- 배치 처리량을 조절한다.

## 체크 질문

- Backpressure가 필요한 이유는 무엇인가
- Kafka lag이 증가한다는 것은 무엇을 의미하는가
- Consumer 수를 늘리는 것이 항상 해결책이 아닌 이유는 무엇인가

## 실무 설계 참고

### 대표 장애 시나리오

송금 이벤트 유입량이 ledger 처리량보다 커져 Kafka lag이 계속 증가한다.

### 잘못된 구현 예시

~~~text
Consumer 수만 늘리면 무조건 처리량이 오른다고 생각한다.
~~~

### 좋은 구현 예시

~~~text
유입 제한, Consumer 처리량 조정, DB 병목 제거, lag 알림을 함께 설계한다.
~~~

### 대안과 선택 이유

서비스 간 REST 호출만으로 후속 처리를 연결하는 방식도 있지만, 원장이나 정산 서비스 장애가 송금 응답에 직접 영향을 준다. PayFlow는 Kafka와 Outbox로 시간적 결합을 낮추고, Consumer 멱등성과 DLQ로 중복/실패를 감당하는 방식이 더 적합하다.

### PayFlow에서 확인할 위치

Kafka lag dashboard, ledger DB metrics, rate limit config

### 면접에서 설명하기

Backpressure는 느린 쪽의 한계를 빠른 쪽에 알려 시스템 전체 붕괴를 막는 개념이다.

### 관련 문서

33, 53, 69, 79

## 질문의 질문으로 더 깊게 보기

### 이 문서에서 잡아야 할 CS 감각

이 구간의 핵심은 메시징 시스템이 비동기성과 신뢰성을 동시에 다룬다는 점이다. Kafka를 쓰면 결합도는 낮아지지만, 중복, 순서, 지연, 재처리 문제가 생긴다. 이벤트 기반 구조는 Consumer 멱등성이 있을 때 비로소 안전해진다.

### 질문의 질문

아래 질문들은 답을 외우기 위한 질문이 아니다. 하나씩 답하면서 "왜 그런 설계가 필요한지"를 설명하는 연습을 하기 위한 질문이다.

- Backpressure 개념이 없다면 PayFlow에서 가장 먼저 어떤 장애가 생기는가?
- 이 개념은 정확성, 성능, 보안, 운영성 중 무엇을 가장 크게 개선하는가?
- 반대로 이 개념을 잘못 적용하면 어떤 복잡도가 추가되는가?
- 이 개념을 적용하면 어떤 문제가 해결되지만, 어떤 비용이나 복잡도가 새로 생기는가?
- 장애가 발생했을 때 이 개념은 문제를 예방하는가, 감지하는가, 복구하는가?
- PayFlow 코드에서 이 개념을 확인하려면 어떤 서비스, 테이블, 로그, 테스트를 봐야 하는가?

### 답안 예시

<details>
<summary>답안 예시 펼치기</summary>

#### 핵심 답변

Backpressure 개념은 PayFlow에서 다음 이유로 중요하다.

- Kafka와 메시징 개념은 PayFlow에서 송금 완료 이후 원장과 정산을 느슨하게 연결하면서도 이벤트를 잃지 않기 위해 필요하다.
- Kafka topic/partition/offset, outbox_event, processed_event, DLQ가 이벤트 처리의 기준이다.
- 이벤트 유실, 중복 소비, 순서 뒤바뀜, Consumer lag, DLQ 적체가 원장 누락이나 중복 기록으로 이어질 수 있다.
- partition key 설계, 처리 후 offset commit, Outbox, processed_event 유니크 제약, DLQ와 재처리 도구로 방어한다.
- 운영에서는 Kafka lag, rebalance 횟수, DLQ 메시지 수, Consumer 처리 시간, processed_event 중복 차단 건수를 본다.

#### 질문에 답하는 방식

좋은 답변은 용어 정의에서 멈추지 않고 다음 순서로 이어져야 한다.

1. 어떤 데이터나 요청을 보호하려는 개념인지 말한다.
2. 그 데이터의 진실의 원천이 어느 서비스에 있는지 말한다.
3. 장애, 중복, 동시성, 지연 중 어떤 상황에서 문제가 생기는지 설명한다.
4. 코드 레벨의 방어 수단과 운영 레벨의 확인 수단을 함께 말한다.

#### PayFlow 예시 답변

```text
Backpressure 개념은 PayFlow에서 처리 능력보다 많은 요청과 이벤트가 들어올 때 시스템을 무너뜨리지 않기 위해 필요하다.
이 개념이 없으면 ledger-service가 DB 병목으로 이벤트를 못 따라가 Kafka lag이 계속 증가할 수 있다.
그래서 코드에서는 Rate Limit, Consumer 처리량 조절, queue lag 모니터링, DB 병목 제거를 적용하고,
운영에서는 Kafka lag 증가율, API queue, DB CPU, 처리량 대비 유입량을 확인해야 한다.
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
Backpressure 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.

