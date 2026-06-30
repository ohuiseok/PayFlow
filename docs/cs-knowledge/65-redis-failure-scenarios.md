# Redis Failure Scenarios

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

## 핵심 개념

### Redis 장애의 유형

Redis 장애는 크게 세 가지 유형으로 나뉜다. 각 유형에 따라 적절한 대응 전략이 다르다.

**연결 장애 (Connection Failure)**
- Redis 프로세스 다운
- 네트워크 파티션으로 Redis에 도달 불가
- 커넥션 풀 고갈

**성능 저하 (Degraded Performance)**
- Redis 메모리 부족으로 Eviction 발생
- 느린 명령 실행 (KEYS *, SMEMBERS 대용량 등)
- AOF/RDB 저장 중 블로킹

**데이터 불일치 (Data Inconsistency)**
- Failover 중 마스터 교체 시 미복제 데이터 유실
- Redis Cluster 리샤딩 중 일시적 접근 불가

### 역할별 장애 영향과 대응 전략

Redis가 어떤 역할을 하는지에 따라 장애의 영향도와 대응이 완전히 다르다.

**역할 1: 캐시 (Cache)**

영향도: 낮음. Redis 장애 시 캐시 miss가 발생하고 모든 요청이 DB로 간다.

대응: DB fallback. 단, 갑작스러운 DB 부하 증가를 조심해야 한다.

```java
public UserProfile getUserProfile(String userId) {
    try {
        UserProfile cached = redis.get("user:" + userId);
        if (cached != null) return cached;
    } catch (RedisException e) {
        log.warn("Redis unavailable, falling back to DB", e);
        // Redis 장애 시 DB로 바로 조회 (캐시 없이)
    }
    return userRepository.findById(userId);
}
```

Cache Stampede 위험: Redis가 갑자기 다운되면 수천 개의 요청이 동시에 DB로 몰린다. Circuit Breaker를 두어 Redis 에러 비율이 높으면 Redis 시도 자체를 건너뛰는 것도 방법이다.

**역할 2: 분산 락 (Distributed Lock)**

영향도: 높음. 락을 획득할 수 없으면 동시성 제어가 불가능하다.

대응: 보수적 실패(fail-safe). 결제처럼 중요한 명령은 락을 못 잡으면 요청을 거부해야 한다.

```java
public void withdraw(String walletId, BigDecimal amount) {
    String lockKey = "lock:wallet:" + walletId;
    String ownerToken = UUID.randomUUID().toString();

    boolean lockAcquired;
    try {
        lockAcquired = redis.setNx(lockKey, ownerToken, 5000);
    } catch (RedisException e) {
        log.error("Redis unavailable for distributed lock", e);
        // 락을 얻을 수 없음 → 결제 거부 (불확실한 상태에서 돈 이동 금지)
        throw new LockUnavailableException("System temporarily unavailable");
    }

    if (!lockAcquired) {
        throw new ConcurrentModificationException("Wallet is being modified");
    }

    try {
        doWithdraw(walletId, amount);
    } finally {
        releaseLock(lockKey, ownerToken);
    }
}
```

락 없이 계속 처리하면 동시 접근이 발생할 수 있다. DB의 `SELECT FOR UPDATE`가 대안이 될 수 있지만, DB 커넥션을 더 오래 점유한다.

**역할 3: 멱등성 저장소 (Idempotency Store)**

영향도: 중간. Redis에 처리 기록을 저장했는데 Redis가 죽으면 기록이 사라져 중복 처리가 발생할 수 있다.

대응: DB를 멱등성 저장소의 주 저장소로 사용하고, Redis는 성능 보조로만 쓴다. 또는 Redis 장애 시 멱등성 확인을 DB에서 수행한다.

### Redis Sentinel과 Redis Cluster의 Failover

**Redis Sentinel**은 마스터를 감시하고 장애 시 자동으로 Replica를 마스터로 승격한다. Failover 과정은 수 초에서 수십 초가 걸릴 수 있다. 이 시간 동안 새 마스터로 연결이 전환된다.

문제는 마스터가 죽기 직전에 쓴 데이터가 Replica에 복제되지 않을 수 있다는 것이다. 이 데이터는 Failover 후 사라진다. 분산 락의 경우, 락을 잡은 프로세스가 있었는데 Failover 후 락이 사라지면, 다른 프로세스가 같은 락을 잡을 수 있다.

**Redis Cluster**는 데이터를 여러 샤드에 분산한다. 특정 샤드 마스터 장애 시 해당 샤드의 Replica가 승격된다. 샤드별 Failover이므로 클러스터 전체가 다운되지 않지만, 특정 키가 있는 샤드 장애 시 그 키에 대한 작업은 Failover 완료까지 실패한다.

### Redis 메모리 부족과 Eviction

Redis 메모리가 가득 차면 설정된 Eviction Policy에 따라 기존 키를 삭제한다.

```
# redis.conf 설정 예시
maxmemory 2gb
maxmemory-policy allkeys-lru  # 가장 오래전에 사용된 키부터 삭제
```

분산 락이나 멱등성 키가 Eviction되면 치명적이다. 이를 막으려면 중요 키에 `OBJECT FREQ`를 높게 유지하거나, 별도 Redis 인스턴스를 사용해야 한다. 캐시용 Redis와 락/멱등성용 Redis를 분리하면 Eviction 정책을 다르게 적용할 수 있다.

```
캐시용 Redis: maxmemory-policy allkeys-lru (캐시는 Eviction 가능)
락/멱등성용 Redis: maxmemory-policy noeviction (중요 데이터는 절대 삭제 안 함)
```

### 모니터링과 이상 감지

```
# 주요 Redis 메트릭
redis-cli INFO stats | grep -E "evicted_keys|rejected_connections"
redis-cli INFO memory | grep -E "used_memory_human|maxmemory_human"
redis-cli INFO replication | grep -E "role|connected_slaves|master_link_status"
redis-cli INFO clients | grep -E "connected_clients|blocked_clients"
```

알림을 설정해야 하는 임계값:
- `evicted_keys` 증가: 메모리 부족 신호
- `rejected_connections` 발생: 커넥션 풀 부족
- `master_link_status: down`: Replica가 마스터와 연결 끊김
- `connected_clients` 급증: 커넥션 누수 가능성

## PayFlow 연결

PayFlow에서 Redis를 분산 락에 사용한다면 Redis 장애는 지갑 동시성 제어에 직접 영향을 준다. 락을 얻을 수 없을 때 송금을 계속 처리할지, 실패시킬지 결정해야 한다.

결제 시스템에서는 불확실한 상태에서 돈을 움직이는 것보다 명확히 실패시키는 편이 안전하다.

## 실무 포인트

- Redis 장애 시 핵심 결제 명령은 보수적으로 실패시킨다.
- 캐시 장애는 DB fallback을 고려하되 부하 폭증을 조심한다.
- 락 만료와 네트워크 지연을 고려한다.
- Redis 모니터링과 알림을 둔다.
- DB 제약조건으로 2차 방어선을 만든다.

## 체크 질문

- Redis 락을 얻지 못하면 송금을 계속 처리해도 되는가
- 캐시 장애와 락 저장소 장애는 왜 다르게 다뤄야 하는가
- Redis 장애 시 DB 부하가 증가할 수 있는 이유는 무엇인가

## 실무 설계 참고

### 대표 장애 시나리오

Redis 장애로 분산 락을 얻지 못하는데도 송금 처리를 계속한다.

### 잘못된 구현 예시

~~~text
Redis는 빠르고 안정적이니 장애 정책이 필요 없다고 생각한다.
~~~

### 좋은 구현 예시

~~~text
락 장애와 캐시 장애를 구분하고 핵심 결제 명령은 보수적으로 실패시킨다.
~~~

### 대안과 선택 이유

Redis 장애는 transfer-service의 송금 락 경로에 직접 영향을 준다. settlement-service는 Redis를 사용하지 않으므로 정산 장애와는 별도 경로다.

### PayFlow에서 확인할 위치

Redis connection config, lock acquisition path, fallback policy

### 면접에서 설명하기

Redis 장애 시 어떤 기능을 멈추고 어떤 기능을 degrade할지 미리 정해야 한다.

### 관련 문서

63, 64, 66

## 질문의 질문으로 더 깊게 보기

### 이 문서에서 잡아야 할 CS 감각

이 구간의 핵심은 장애를 예외 상황이 아니라 설계 입력으로 보는 것이다. Timeout, Retry, Circuit Breaker, Graceful Shutdown, 배포 전략은 모두 장애가 났을 때 피해를 제한하고 복구 가능하게 만드는 장치다.

### 질문의 질문

아래 질문들은 답을 외우기 위한 질문이 아니다. 하나씩 답하면서 "왜 그런 설계가 필요한지"를 설명하는 연습을 하기 위한 질문이다.

- Redis Failure Scenarios 개념이 없다면 PayFlow에서 가장 먼저 어떤 장애가 생기는가?
- 이 개념은 정확성, 성능, 보안, 운영성 중 무엇을 가장 크게 개선하는가?
- 반대로 이 개념을 잘못 적용하면 어떤 복잡도가 추가되는가?
- 이 개념을 적용하면 어떤 문제가 해결되지만, 어떤 비용이나 복잡도가 새로 생기는가?
- 장애가 발생했을 때 이 개념은 문제를 예방하는가, 감지하는가, 복구하는가?
- PayFlow 코드에서 이 개념을 확인하려면 어떤 서비스, 테이블, 로그, 테스트를 봐야 하는가?

### 답안 예시

<details>
<summary>답안 예시 펼치기</summary>

#### 핵심 답변

Redis Failure Scenarios 개념은 PayFlow에서 다음 이유로 중요하다.

- Redis는 PayFlow에서 분산 락이나 캐시를 통해 동시성 제어와 성능 개선을 돕지만, 장애 시 정책이 명확해야 한다.
- 서비스 상태, 배포 버전, DB 마이그레이션 이력, health check, retry/circuit breaker 상태가 기준이다.
- Redis 락을 얻지 못했는데 송금을 계속 처리하면 같은 지갑에 동시 변경이 발생할 수 있다.
- 락 TTL, 소유자 확인 후 해제, Redis 장애 시 보수적 실패, DB 트랜잭션과 제약조건을 함께 사용한다.
- 운영에서는 timeout 수, retry 수, circuit open 수, readiness 실패, 배포 후 에러율, Consumer rebalance를 본다.

#### 질문에 답하는 방식

좋은 답변은 용어 정의에서 멈추지 않고 다음 순서로 이어져야 한다.

1. 어떤 데이터나 요청을 보호하려는 개념인지 말한다.
2. 그 데이터의 진실의 원천이 어느 서비스에 있는지 말한다.
3. 장애, 중복, 동시성, 지연 중 어떤 상황에서 문제가 생기는지 설명한다.
4. 코드 레벨의 방어 수단과 운영 레벨의 확인 수단을 함께 말한다.

#### PayFlow 예시 답변

```text
Redis Failure Scenarios 개념은 PayFlow에서 Redis 장애가 캐시 장애인지 락 장애인지 구분해 결제 명령을 안전하게 처리하기 위해 필요하다.
이 개념이 없으면 Redis 락을 사용할 수 없는데 송금을 계속 진행해 지갑 동시성 제어가 사라질 수 있다.
그래서 코드에서는 핵심 결제 명령은 보수적으로 실패시키고 캐시 장애는 제한된 DB fallback을 적용하며,
운영에서는 Redis 연결 실패, 락 실패율, fallback DB 부하를 확인해야 한다.
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
Redis Failure Scenarios 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.

