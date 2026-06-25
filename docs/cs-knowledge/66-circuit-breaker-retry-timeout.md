# Circuit Breaker Retry Timeout

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

## 핵심 개념

### Timeout: 기다림의 한계를 정하는 이유

외부 서비스 호출 시 응답을 무한정 기다리면 어떻게 되는가? 스레드가 응답을 기다리는 동안 다른 요청을 처리할 수 없다. 서버의 스레드 풀이 가득 차면 새 요청도 처리하지 못한다. 하나의 느린 외부 서비스가 전체 시스템을 마비시킬 수 있다.

Timeout은 이 대기 시간의 상한을 정한다.

```java
// RestTemplate에 Timeout 설정
RestTemplate restTemplate = new RestTemplateBuilder()
    .connectTimeout(Duration.ofSeconds(1))   // 연결 수립 최대 1초
    .readTimeout(Duration.ofSeconds(3))      // 응답 수신 최대 3초
    .build();

// WebClient에 Timeout 설정
WebClient webClient = WebClient.builder()
    .clientConnector(new ReactorClientHttpConnector(
        HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
            .responseTimeout(Duration.ofSeconds(3))
    ))
    .build();
```

**Timeout의 두 종류**

- **Connect Timeout**: TCP 연결을 맺는 데 걸리는 시간. 서버가 아예 응답하지 않을 때 걸린다.
- **Read Timeout**: 연결은 됐지만 응답이 오기까지의 시간. 서버가 처리 중인 데 시간이 걸릴 때 걸린다.

**적절한 Timeout 값 설정**

P99 응답 시간을 기준으로 설정한다. 정상 상황에서 99%의 요청이 200ms 이내면, Timeout을 500ms~1000ms로 설정한다. 너무 짧으면 정상 요청도 실패하고, 너무 길면 장애 상황에서 의미가 없다.

### Retry: 재시도의 조건과 위험

일시적 오류(네트워크 순간 단절, 503 Service Unavailable)는 재시도하면 성공할 수 있다. 하지만 재시도가 항상 안전한 것은 아니다.

**재시도해도 안전한 경우**: 멱등한 작업
- GET 요청 (조회)
- 멱등성 키가 있는 결제 API
- 상태 확인 API

**재시도하면 위험한 경우**: 멱등하지 않은 작업
- 멱등성 키 없이 재시도하는 송금 요청 → 이중 출금
- 상태가 이미 변경된 요청의 재시도 → 중복 처리

```java
// 멱등성 키를 포함한 재시도 안전 설계
public TransferResult transfer(TransferRequest request) {
    // 클라이언트가 제공한 멱등성 키를 외부 서비스에 전달
    HttpHeaders headers = new HttpHeaders();
    headers.set("Idempotency-Key", request.getIdempotencyKey());

    return restTemplate.exchange(
        "http://wallet-service/withdraw",
        HttpMethod.POST,
        new HttpEntity<>(request, headers),
        TransferResult.class
    ).getBody();
    // 이제 재시도해도 wallet-service가 중복 처리를 막음
}
```

**Exponential Backoff**: 재시도 간격을 지수적으로 늘린다. 즉시 재시도하면 오류를 유발한 원인이 아직 해결되지 않았을 가능성이 높다.

```
재시도 1: 100ms 후
재시도 2: 200ms 후
재시도 3: 400ms 후
재시도 4: 800ms 후
최대 4회 후 실패
```

### Circuit Breaker: 장애 전파 차단의 원리

Circuit Breaker는 전기 회로 차단기에서 이름을 따왔다. 과부하 시 회로를 차단해서 전체 시스템을 보호하는 것처럼, 실패하는 서비스 호출을 차단해서 나머지 시스템을 보호한다.

**세 가지 상태**

```
CLOSED (정상)
- 모든 요청을 통과
- 실패율을 측정
- 실패율이 임계값(예: 50%)을 넘으면 → OPEN

OPEN (차단)
- 모든 요청을 즉시 실패 처리 (fallback 실행)
- 외부 서비스 호출 없음
- 일정 시간(예: 30초) 후 → HALF-OPEN

HALF-OPEN (탐침)
- 소수의 요청만 통과
- 성공하면 → CLOSED (회복)
- 실패하면 → OPEN (다시 차단)
```

```java
// Resilience4j Circuit Breaker 설정
CircuitBreakerConfig config = CircuitBreakerConfig.custom()
    .slidingWindowSize(10)           // 최근 10개 요청 기준
    .failureRateThreshold(50)        // 50% 이상 실패 시 OPEN
    .waitDurationInOpenState(Duration.ofSeconds(30))  // 30초 후 HALF-OPEN
    .permittedNumberOfCallsInHalfOpenState(3)  // HALF-OPEN에서 3개 허용
    .build();

CircuitBreaker circuitBreaker = CircuitBreaker.of("walletService", config);

// 사용
try {
    WalletBalance balance = circuitBreaker.executeSupplier(
        () -> walletServiceClient.getBalance(walletId)
    );
} catch (CallNotPermittedException e) {
    // Circuit이 OPEN 상태 → fallback 실행
    return getBalanceFromCache(walletId);
}
```

### Fallback 설계의 중요성

Circuit Breaker가 OPEN되면 fallback이 실행된다. fallback은 비즈니스적으로 안전해야 한다.

**안전한 fallback**: 캐시에서 이전 값 반환, "일시적으로 조회 불가" 응답, 보수적인 기본값 사용

**위험한 fallback**: 빈 잔액(0원)을 반환하는 것 → 잔액이 0이면 출금 거부될 수 있음. 실제 잔액이 있는데 잘못된 fallback으로 서비스 불가가 발생.

결제 명령(출금, 송금)의 경우 fallback으로 허용하는 것은 위험하다. 외부 서비스 없이는 정확한 처리가 불가능하므로, "서비스 일시 불가" 에러를 반환하는 것이 더 안전하다.

### 세 패턴의 조합

```
요청 → [Circuit Breaker 확인] → OPEN이면 즉시 fallback
        ↓ CLOSED
       [외부 호출] → 응답 대기
        ↓ Timeout 초과
       [Retry] → 재시도 (멱등한 경우만)
        ↓ 최대 재시도 초과
       실패 처리 → Circuit Breaker 실패 카운트 증가
```

### 흔한 오해와 함정

**"Timeout이 길면 더 안전하다"**: 오히려 장애 상황에서 회복이 느려진다. 하나의 요청이 긴 시간 스레드를 점유하면 서버가 새 요청을 처리하지 못한다.

**"재시도는 항상 도움이 된다"**: 과부하 상태의 서비스에 재시도하면 더 큰 부하를 준다. Retry Storm을 유발할 수 있다.

**"Circuit Breaker는 에러를 숨긴다"**: Circuit이 OPEN되면 요청이 즉시 실패한다. 에러를 숨기는 게 아니라 이미 실패가 예상되는 요청을 빨리 실패시켜 시스템 자원을 아끼는 것이다.

## PayFlow 연결

`transfer-service`가 `wallet-service`를 호출할 때 응답이 느리면 스레드가 점유된다. 타임아웃이 없으면 요청이 계속 쌓이고 장애가 전체로 퍼질 수 있다.

재시도는 일시 장애에 유용하지만, 송금 같은 명령 요청은 멱등성 없이 재시도하면 중복 차감이 발생할 수 있다.

## 실무 포인트

- 모든 외부 호출에는 타임아웃을 둔다.
- 재시도는 멱등한 작업에만 신중히 적용한다.
- Circuit Breaker로 실패한 서비스 호출을 빠르게 차단한다.
- fallback은 비즈니스적으로 안전해야 한다.
- 재시도 횟수와 간격을 제한한다.

## 체크 질문

- 타임아웃이 없으면 어떤 장애가 발생할 수 있는가
- 송금 요청 재시도에 멱등성이 필요한 이유는 무엇인가
- Circuit Breaker는 어떤 문제를 줄여주는가

## 실무 설계 참고

### 대표 장애 시나리오

wallet-service 장애 중 transfer-service가 계속 대기하고 재시도해 장애가 퍼진다.

### 잘못된 구현 예시

~~~text
재시도만 많이 하면 일시 장애가 해결된다고 생각한다.
~~~

### 좋은 구현 예시

~~~text
Timeout, Retry 제한, Circuit Breaker, 안전한 fallback을 함께 적용한다.
~~~

### 대안과 선택 이유

장애가 나면 사람이 재시작하거나 배포를 되돌리는 방식도 가능하지만, 결제 시스템에서는 장애 전파 속도가 빠르고 수동 대응은 늦다. PayFlow는 Timeout, Retry 제한, Circuit Breaker, Bulkhead, Graceful Shutdown, 호환 배포를 통해 피해 범위를 자동으로 줄이는 편이 낫다.

### PayFlow에서 확인할 위치

Resilience4j config, Feign/HTTP client config, transfer-service logs

### 면접에서 설명하기

장애 대응 패턴은 실패를 없애는 것이 아니라 실패가 퍼지는 속도와 범위를 줄이는 것이다.

### 관련 문서

67, 68, 69

## 질문의 질문으로 더 깊게 보기

### 이 문서에서 잡아야 할 CS 감각

이 구간의 핵심은 장애를 예외 상황이 아니라 설계 입력으로 보는 것이다. Timeout, Retry, Circuit Breaker, Graceful Shutdown, 배포 전략은 모두 장애가 났을 때 피해를 제한하고 복구 가능하게 만드는 장치다.

### 질문의 질문

아래 질문들은 답을 외우기 위한 질문이 아니다. 하나씩 답하면서 "왜 그런 설계가 필요한지"를 설명하는 연습을 하기 위한 질문이다.

- Circuit Breaker Retry Timeout 개념이 없다면 PayFlow에서 가장 먼저 어떤 장애가 생기는가?
- 이 개념은 정확성, 성능, 보안, 운영성 중 무엇을 가장 크게 개선하는가?
- 반대로 이 개념을 잘못 적용하면 어떤 복잡도가 추가되는가?
- 이 개념을 적용하면 어떤 문제가 해결되지만, 어떤 비용이나 복잡도가 새로 생기는가?
- 장애가 발생했을 때 이 개념은 문제를 예방하는가, 감지하는가, 복구하는가?
- PayFlow 코드에서 이 개념을 확인하려면 어떤 서비스, 테이블, 로그, 테스트를 봐야 하는가?

### 답안 예시

<details>
<summary>답안 예시 펼치기</summary>

#### 핵심 답변

Circuit Breaker Retry Timeout 개념은 PayFlow에서 다음 이유로 중요하다.

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
Circuit Breaker Retry Timeout 개념은 PayFlow에서 느린 내부 서비스 호출이 전체 송금 시스템 장애로 번지지 않게 하기 위해 필요하다.
이 개념이 없으면 wallet-service 장애 중 transfer-service가 무제한 대기/재시도해 스레드와 DB 커넥션을 고갈시킬 수 있다.
그래서 코드에서는 Timeout, 제한된 Retry, Circuit Breaker, 안전한 fallback을 설정하고,
운영에서는 timeout 수, retry 수, circuit open 상태, 실패율을 확인해야 한다.
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
Circuit Breaker Retry Timeout 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.

