# Redis Distributed Lock

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

## 핵심 개념

### 분산 락이 필요한 이유

단일 프로세스에서는 `synchronized` 블록이나 `ReentrantLock`으로 동시성을 제어할 수 있다. JVM 메모리 내에서 락 상태를 관리하기 때문이다.

하지만 마이크로서비스 환경에서는 같은 서비스가 여러 인스턴스로 배포된다. 각 인스턴스는 독립된 JVM이므로 서로의 메모리를 공유하지 않는다. 인스턴스 A의 `synchronized`는 인스턴스 B를 막을 수 없다.

```
인스턴스 A: walletId=123 잔액 조회(100원) → 50원 차감 중...
인스턴스 B: walletId=123 잔액 조회(100원) → 80원 차감 중...
결과: 30원이어야 하는데 A는 50원, B는 20원으로 덮어씀
```

분산 환경에서 임계 구역을 보호하려면 모든 인스턴스가 공유하는 외부 저장소에서 락을 관리해야 한다. Redis는 빠른 원자적 연산을 지원하므로 분산 락 저장소로 적합하다.

### Redis 분산 락의 구현 원리

Redis의 `SET NX PX` 명령이 핵심이다.

```
SET lock:wallet:123 {owner_token} NX PX 5000
```

- `NX`: Not eXists. 키가 없을 때만 SET 성공
- `PX 5000`: 5000ms 후 자동 만료 (TTL)
- `{owner_token}`: 락을 누가 잡았는지 식별하는 고유값 (UUID)

이 명령은 원자적이다. "키가 없으면 설정"이 하나의 연산으로 실행되므로 race condition이 없다.

```java
// Redis 분산 락 획득
public boolean acquireLock(String lockKey, String ownerToken, long ttlMs) {
    String result = jedis.set(lockKey, ownerToken, "NX", "PX", ttlMs);
    return "OK".equals(result);
}

// Redis 분산 락 해제 (Lua 스크립트로 원자적 실행)
private static final String RELEASE_SCRIPT =
    "if redis.call('get', KEYS[1]) == ARGV[1] then " +
    "  return redis.call('del', KEYS[1]) " +
    "else " +
    "  return 0 " +
    "end";

public boolean releaseLock(String lockKey, String ownerToken) {
    Object result = jedis.eval(RELEASE_SCRIPT,
        Collections.singletonList(lockKey),
        Collections.singletonList(ownerToken)
    );
    return Long.valueOf(1).equals(result);
}
```

### 락 해제 시 owner 확인이 필수인 이유

락 해제 전에 반드시 자신이 잡은 락인지 확인해야 한다. TTL이 만료되어 락이 자동 해제되고 다른 인스턴스가 새로 락을 잡은 상황에서, 원래 인스턴스가 "자신의 락"을 해제하려 하면 다른 인스턴스의 락을 삭제하는 문제가 생긴다.

```
시나리오:
1. A가 lock:wallet:123을 잡음 (owner=A)
2. A의 처리가 TTL(5초)보다 오래 걸림
3. TTL 만료 → 락 자동 해제
4. B가 lock:wallet:123을 잡음 (owner=B)
5. A의 처리 완료 → DEL lock:wallet:123 실행
6. B의 락이 삭제됨!
7. C가 lock:wallet:123을 잡음 → B와 C 동시 접근
```

owner token을 확인하고 일치할 때만 삭제하면 이 문제를 막을 수 있다. GET과 DEL을 분리하면 두 연산 사이에 race condition이 생기므로, Lua 스크립트로 원자적으로 실행해야 한다.

### TTL 설정의 딜레마

**TTL이 너무 짧으면**: 처리가 끝나기 전에 TTL이 만료되어 다른 프로세스가 같은 락을 잡을 수 있다.

**TTL이 너무 길면**: 락을 잡은 프로세스가 죽었을 때, 락이 오랫동안 해제되지 않아 모든 요청이 차단된다.

**해결책: Watchdog 패턴**

Redisson 같은 라이브러리는 Watchdog을 통해 락 보유 중 자동으로 TTL을 연장한다.

```java
// Redisson 예시: 락 보유 중 자동 TTL 연장
RLock lock = redissonClient.getLock("wallet:" + walletId);
try {
    // 10초 TTL로 락 획득, 하지만 watchdog이 자동 연장
    boolean acquired = lock.tryLock(0, 10, TimeUnit.SECONDS);
    if (acquired) {
        processWithdrawal(walletId, amount);
    }
} finally {
    lock.unlock();
}
```

### 분산 락과 DB 트랜잭션의 관계

분산 락은 여러 인스턴스 간 동시 접근을 막는다. DB 트랜잭션은 DB 레벨에서 ACID를 보장한다. 이 둘은 보완 관계이지 대체 관계가 아니다.

```
분산 락 없이 DB 트랜잭션만 사용:
- SELECT ... FOR UPDATE로 행 락을 잡을 수 있음
- DB 커넥션을 락이 해제될 때까지 점유
- 많은 동시 요청 시 DB 커넥션 풀 고갈 위험

분산 락 + DB 트랜잭션:
- Redis 락으로 접근을 사전 차단 (빠름)
- DB에는 이미 직렬화된 요청만 도달
- DB 커넥션 절약
- 단, Redis 락이 우회되는 경우를 위해 DB 제약도 유지
```

### Redlock 알고리즘과 단일 Redis의 한계

단일 Redis 노드를 사용하면 그 노드 장애 시 락 시스템 전체가 중단된다. 또한 Redis Sentinel이나 Cluster를 사용할 때, Failover 과정에서 마스터가 바뀌면서 같은 락이 두 클라이언트에게 발급되는 상황이 이론적으로 가능하다.

Redis 창시자 Salvatore Sanfilippo가 제안한 Redlock은 독립된 Redis 노드 5개 중 과반수(3개)에서 락을 획득해야 유효한 락으로 간주하는 알고리즘이다. 하지만 Martin Kleppmann이 이 알고리즘의 안전성에 의문을 제기했고, 이에 대한 논쟁이 있다.

실용적 관점에서는 단일 Redis + DB 레벨 제약 조건을 최종 방어선으로 두는 것이 많은 결제 시스템에서 사용하는 방법이다.

### 흔한 오해와 함정

**"분산 락이 있으면 DB 트랜잭션이 필요 없다"**: 아니다. Redis 장애나 락 TTL 만료, 버그로 인한 락 우회 상황에서 DB 제약이 최종 방어선이 된다.

**"락 획득 실패 시 무한 루프로 재시도한다"**: 스핀 락은 CPU를 낭비하고 Redis에 부하를 준다. 지수 백오프로 재시도하거나 즉시 실패를 반환해야 한다.

## PayFlow 연결

PayFlow에서 같은 지갑에 대한 동시 송금 요청을 제어하기 위해 Redis 분산 락을 사용할 수 있다. 예를 들어 `wallet:{walletId}`를 락 키로 잡고 잔액 변경을 수행한다.

## 실무 포인트

- 락에는 TTL을 반드시 둔다.
- 락 해제는 자신이 잡은 락인지 확인한 뒤 수행한다.
- 락 획득 실패 시 정책을 정한다.
- 락만 믿지 말고 DB 제약과 트랜잭션도 함께 사용한다.
- Redis 장애 시 동작 방식을 결정한다.

## 체크 질문

- 분산 락이 필요한 이유는 무엇인가
- Redis 락에 TTL이 없으면 어떤 문제가 생기는가
- 분산 락과 DB 트랜잭션을 함께 고려해야 하는 이유는 무엇인가

## 실무 설계 참고

### 대표 장애 시나리오

두 transfer-service 인스턴스가 같은 walletId에 동시에 출금 로직을 실행한다.

### 잘못된 구현 예시

~~~text
synchronized나 JVM lock이 여러 컨테이너에서도 통한다고 생각한다.
~~~

### 좋은 구현 예시

~~~text
Redis lock key, TTL, owner token, DB transaction을 함께 사용한다.
~~~

### 대안과 선택 이유

서비스 간 REST 호출만으로 후속 처리를 연결하는 방식도 있지만, 원장이나 정산 서비스 장애가 송금 응답에 직접 영향을 준다. PayFlow는 Kafka와 Outbox로 시간적 결합을 낮추고, Consumer 멱등성과 DLQ로 중복/실패를 감당하는 방식이 더 적합하다.

### PayFlow에서 확인할 위치

wallet lock key design, Redis config, withdraw transaction

### 면접에서 설명하기

분산 락은 여러 프로세스 사이의 동시성을 제어하지만, DB 제약을 대체하지는 않는다.

### 관련 문서

25, 26, 65

## 질문의 질문으로 더 깊게 보기

### 이 문서에서 잡아야 할 CS 감각

이 구간의 핵심은 메시징 시스템이 비동기성과 신뢰성을 동시에 다룬다는 점이다. Kafka를 쓰면 결합도는 낮아지지만, 중복, 순서, 지연, 재처리 문제가 생긴다. 이벤트 기반 구조는 Consumer 멱등성이 있을 때 비로소 안전해진다.

### 질문의 질문

아래 질문들은 답을 외우기 위한 질문이 아니다. 하나씩 답하면서 "왜 그런 설계가 필요한지"를 설명하는 연습을 하기 위한 질문이다.

- Redis Distributed Lock 개념이 없다면 PayFlow에서 가장 먼저 어떤 장애가 생기는가?
- 이 개념은 정확성, 성능, 보안, 운영성 중 무엇을 가장 크게 개선하는가?
- 반대로 이 개념을 잘못 적용하면 어떤 복잡도가 추가되는가?
- 이 개념을 적용하면 어떤 문제가 해결되지만, 어떤 비용이나 복잡도가 새로 생기는가?
- 장애가 발생했을 때 이 개념은 문제를 예방하는가, 감지하는가, 복구하는가?
- PayFlow 코드에서 이 개념을 확인하려면 어떤 서비스, 테이블, 로그, 테스트를 봐야 하는가?

### 답안 예시

<details>
<summary>답안 예시 펼치기</summary>

#### 핵심 답변

Redis Distributed Lock 개념은 PayFlow에서 다음 이유로 중요하다.

- Redis는 PayFlow에서 분산 락이나 캐시를 통해 동시성 제어와 성능 개선을 돕지만, 장애 시 정책이 명확해야 한다.
- Kafka topic/partition/offset, outbox_event, processed_event, DLQ가 이벤트 처리의 기준이다.
- Redis 락을 얻지 못했는데 송금을 계속 처리하면 같은 지갑에 동시 변경이 발생할 수 있다.
- 락 TTL, 소유자 확인 후 해제, Redis 장애 시 보수적 실패, DB 트랜잭션과 제약조건을 함께 사용한다.
- 운영에서는 Kafka lag, rebalance 횟수, DLQ 메시지 수, Consumer 처리 시간, processed_event 중복 차단 건수를 본다.

#### 질문에 답하는 방식

좋은 답변은 용어 정의에서 멈추지 않고 다음 순서로 이어져야 한다.

1. 어떤 데이터나 요청을 보호하려는 개념인지 말한다.
2. 그 데이터의 진실의 원천이 어느 서비스에 있는지 말한다.
3. 장애, 중복, 동시성, 지연 중 어떤 상황에서 문제가 생기는지 설명한다.
4. 코드 레벨의 방어 수단과 운영 레벨의 확인 수단을 함께 말한다.

#### PayFlow 예시 답변

```text
Redis Distributed Lock 개념은 PayFlow에서 여러 인스턴스가 같은 지갑 잔액을 동시에 변경하지 못하게 하기 위해 필요하다.
이 개념이 없으면 transfer-service 인스턴스 두 개가 같은 walletId 출금을 동시에 처리해 잔액이 깨질 수 있다.
그래서 코드에서는 Redis lock key, TTL, owner token 확인 후 해제, DB 트랜잭션을 함께 적용하고,
운영에서는 lock 획득 실패, lock wait, TTL 만료, 동시성 테스트 결과를 확인해야 한다.
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
Redis Distributed Lock 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.

