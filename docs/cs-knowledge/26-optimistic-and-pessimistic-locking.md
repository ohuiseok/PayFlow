# Optimistic And Pessimistic Locking

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

## 핵심 개념

### 두 가지 락 전략의 기본 철학

**낙관적 락(Optimistic Lock)**: "충돌이 드물 것이다"라고 가정한다. 데이터를 읽을 때 락을 잡지 않고, 변경할 때 내가 읽은 이후 다른 트랜잭션이 변경했는지 확인한다. 버전 번호나 타임스탬프를 사용한다. 충돌이 발생하면 변경이 실패하고 재시도해야 한다.

**비관적 락(Pessimistic Lock)**: "충돌이 자주 발생할 것이다"라고 가정한다. 데이터를 읽는 순간부터 다른 트랜잭션의 변경을 막는다. 충돌 자체를 예방하지만 락 보유 시간이 길어질 수 있다.

### 낙관적 락의 동작 원리

JPA에서 `@Version` 어노테이션으로 구현한다. 테이블에 version 컬럼을 추가하고, 변경할 때 version을 조건으로 걸어 원자적으로 증가시킨다.

```java
@Entity
public class Wallet {
    @Id
    private Long id;

    private BigDecimal balance;

    @Version
    private Long version;  // 낙관적 락 버전 필드
}
```

JPA가 생성하는 UPDATE SQL:
```sql
UPDATE wallet
SET balance = ?, version = version + 1
WHERE id = ? AND version = ?  -- 현재 버전 확인
```

다른 트랜잭션이 먼저 변경해 version이 바뀌었으면 `affected_rows = 0`이 되고, JPA는 `OptimisticLockException`을 던진다.

```text
T1: version=1인 wallet 조회
T2: version=1인 wallet 조회
T2: UPDATE ... WHERE version=1 -> 성공, version=2
T1: UPDATE ... WHERE version=1 -> 실패(version이 이미 2), OptimisticLockException
T1: 재시도 (다시 조회 -> 잔액 확인 -> 변경 시도)
```

### 비관적 락의 동작 원리

JPA에서 `LockModeType.PESSIMISTIC_WRITE`를 사용하면 `SELECT ... FOR UPDATE`가 실행된다.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT w FROM Wallet w WHERE w.id = :id")
Optional<Wallet> findByIdForUpdate(@Param("id") Long id);
```

또는 Spring Data JPA의 메서드에 어노테이션으로:

```java
Optional<Wallet> findById(Long id, LockModeType lockMode);
// walletRepository.findById(walletId, LockModeType.PESSIMISTIC_WRITE);
```

실행되는 SQL:
```sql
SELECT * FROM wallet WHERE id = ? FOR UPDATE
```

이 쿼리를 실행하는 순간 해당 행에 배타 락이 걸린다. 다른 트랜잭션은 같은 행에 `FOR UPDATE`나 `FOR SHARE`를 실행하면 대기한다.

### 락 모드의 종류

**PESSIMISTIC_READ (공유 락)**: `SELECT ... FOR SHARE`. 다른 트랜잭션의 읽기는 허용하지만 쓰기는 막는다.

**PESSIMISTIC_WRITE (배타 락)**: `SELECT ... FOR UPDATE`. 다른 트랜잭션의 읽기(FOR SHARE 포함)와 쓰기 모두 막는다.

**OPTIMISTIC**: 트랜잭션 종료 시 version을 확인한다.

**OPTIMISTIC_FORCE_INCREMENT**: 변경 여부와 무관하게 version을 증가시킨다.

### 낙관적 락 vs 비관적 락 선택 기준

| 기준 | 낙관적 락 | 비관적 락 |
|---|---|---|
| 충돌 빈도 | 낮음 | 높음 |
| 충돌 시 비용 | 재시도 (사용자 불편) | 없음 (대기 후 처리) |
| 성능(throughput) | 높음 (락 없이 읽기) | 낮음 (락 대기) |
| 데드락 위험 | 낮음 | 높음 |
| 적합한 데이터 | 사용자 프로필, 설정 등 | 잔액, 재고 등 금전/수량 데이터 |

PayFlow에서 지갑 잔액은 동시 출금 요청이 충돌할 가능성이 있고 충돌 시 재시도가 복잡하므로 비관적 락이 더 안전하다. 사용자 프로필 변경처럼 충돌 가능성이 낮고 재시도가 쉬운 경우에는 낙관적 락이 적합하다.

### 낙관적 락 충돌 처리 패턴

낙관적 락 충돌 시 `OptimisticLockException`이 발생한다. 이를 잡아서 재시도 로직을 구현해야 한다.

```java
@Retryable(
    value = OptimisticLockingFailureException.class,
    maxAttempts = 3,
    backoff = @Backoff(delay = 100, multiplier = 2)
)
public void updateWallet(Long walletId, BigDecimal amount) {
    Wallet wallet = walletRepository.findById(walletId).orElseThrow();
    wallet.adjustBalance(amount);
    walletRepository.save(wallet);
}
```

재시도를 무한히 하면 다른 문제가 생기므로 최대 재시도 횟수와 backoff 전략을 반드시 설정해야 한다.

### DB 조건부 UPDATE: 락 없는 대안

비관적 락 없이도 원자적으로 잔액을 처리할 수 있다.

```sql
UPDATE wallet
SET balance = balance - 5000,
    updated_at = NOW()
WHERE id = 1
  AND balance >= 5000;  -- 잔액 부족 조건 DB에서 직접 검사
```

애플리케이션은 `affected_rows`를 확인해 처리 성공 여부를 판단한다. 이 방식은 락 없이도 원자적이어서 데드락 위험이 없다. 다만 변경 전 잔액 값을 반환받아야 할 때는 추가 쿼리가 필요하다.

### 분산 환경에서의 낙관적/비관적 락 한계

단일 DB 서버에서는 낙관적/비관적 락이 잘 동작한다. 하지만 다음 상황에서는 한계가 있다.

- **DB 복제(Replica)**: 읽기를 Replica에서 하고 쓰기를 Primary에서 하는 구조에서 Replica 지연 때문에 낙관적 락의 version이 오래된 값을 볼 수 있다.
- **다중 서비스**: 지갑이 wallet-service에 있고 transfer-service에서 잔액을 변경하는 구조에서는 DB 락이 아닌 Redis 분산 락이 필요하다.
- **Saga 패턴**: 여러 서비스에 걸친 트랜잭션에서는 단일 DB 락으로 전체를 보호할 수 없다.

### 흔한 오해와 함정

**"낙관적 락은 성능이 좋으니 항상 낫다"**: 충돌이 잦으면 재시도가 반복되어 오히려 성능이 나빠지고 사용자 경험도 나빠진다. 잔액처럼 경합이 있는 데이터에는 비관적 락이 낫다.

**"@Version만 붙이면 낙관적 락이 완성된다"**: `OptimisticLockException` 처리와 재시도 로직이 없으면 충돌 시 그냥 오류만 반환된다.

**"비관적 락은 데드락이 없다"**: 비관적 락을 잘못 사용하면 데드락이 발생한다. 특히 여러 자원을 순서 없이 잠글 때 위험하다.

**"락 없이 조건부 UPDATE만 쓰면 된다"**: 조건부 UPDATE는 단순한 경우에 유용하지만, 여러 테이블에 걸친 복잡한 변경이나 변경 전 값 확인이 필요한 경우에는 불충분하다.


## PayFlow 연결

지갑 잔액처럼 충돌이 치명적인 데이터는 비관적 락이나 분산 락을 고려할 수 있다. 반면 사용자 프로필처럼 충돌 가능성이 낮은 데이터는 낙관적 락이 적합할 수 있다.

송금 시스템에서는 "성능"보다 "금액 정합성"이 먼저다. 따라서 초과 출금 가능성이 있는 구간은 보수적으로 설계해야 한다.

## 실무 포인트

- 낙관적 락은 충돌 시 예외를 처리하고 재시도해야 한다.
- 비관적 락은 데드락과 대기 시간을 고려해야 한다.
- 락 선택은 데이터 성격과 트래픽 패턴에 따라 달라진다.
- 잔액 변경은 DB 조건 업데이트도 대안이 될 수 있다.

## 체크 질문

- 낙관적 락과 비관적 락의 차이는 무엇인가
- 지갑 잔액에는 어떤 락 전략이 더 안전한가
- 낙관적 락 충돌이 발생하면 어떻게 처리해야 하는가

## 실무 설계 참고

### 대표 장애 시나리오

낙관적 락 충돌이 잦은 지갑에 재시도 정책이 없어 송금 실패가 증가한다.

### 잘못된 구현 예시

~~~text
낙관적 락과 비관적 락을 성능 차이로만 선택한다.
~~~

### 좋은 구현 예시

~~~text
충돌 빈도와 금전 위험을 기준으로 락 전략을 선택하고 실패 시 정책을 둔다.
~~~

### 대안과 선택 이유

애플리케이션 코드에서만 검증하는 방식도 있지만, 동시 요청과 장애 상황에서는 코드 검증만으로 부족하다. PayFlow는 DB 트랜잭션, 락, 유니크 제약, 인덱스, 실행 계획을 함께 사용해 데이터 계층에서도 불변식을 지키는 쪽이 더 안전하다.

### PayFlow에서 확인할 위치

wallet entity version, repository lock mode, withdraw transaction

### 면접에서 설명하기

낙관적 락은 충돌을 나중에 발견하고, 비관적 락은 충돌 가능성을 먼저 막는다.

### 관련 문서

25, 27, 64

## 질문의 질문으로 더 깊게 보기

### 이 문서에서 잡아야 할 CS 감각

이 구간의 핵심은 데이터가 동시에 바뀔 때 어떤 불변식을 지켜야 하는가다. 결제 시스템에서는 "잔액은 음수가 되면 안 된다", "같은 요청은 한 번만 반영되어야 한다" 같은 규칙이 코드보다 더 중요하다.

### 질문의 질문

아래 질문들은 답을 외우기 위한 질문이 아니다. 하나씩 답하면서 "왜 그런 설계가 필요한지"를 설명하는 연습을 하기 위한 질문이다.

- 이 작업에서 반드시 지켜야 하는 데이터 불변식은 무엇인가?
- 락을 잡는 범위를 줄이면 성능은 좋아지지만 어떤 정합성 위험이 생기는가?
- DB 격리 수준으로 해결할 문제와 애플리케이션 레벨에서 해결할 문제를 어떻게 나눌 것인가?
- 이 개념을 적용하면 어떤 문제가 해결되지만, 어떤 비용이나 복잡도가 새로 생기는가?
- 장애가 발생했을 때 이 개념은 문제를 예방하는가, 감지하는가, 복구하는가?
- PayFlow 코드에서 이 개념을 확인하려면 어떤 서비스, 테이블, 로그, 테스트를 봐야 하는가?

### 답안 예시

<details>
<summary>답안 예시 펼치기</summary>

#### 핵심 답변

Optimistic And Pessimistic Locking 개념은 PayFlow에서 다음 이유로 중요하다.

- DB 트랜잭션과 동시성 개념은 PayFlow에서 잔액, 송금 상태, 멱등성 키 같은 핵심 데이터를 깨지지 않게 지키기 위해 필요하다.
- wallet DB의 잔액과 변경 이력, transfer DB의 송금 상태와 idempotency record가 진실의 원천이다.
- 동시 출금, 락 경합, 데드락, 트랜잭션 경계 오류가 발생하면 초과 출금이나 중복 처리가 생길 수 있다.
- DB 트랜잭션, 유니크 제약, 낙관적/비관적 락, 일관된 락 순서, 짧은 트랜잭션으로 방어한다.
- 운영에서는 락 대기 시간, deadlock 로그, 트랜잭션 시간, 중복 키 예외, 잔액 불일치 건수를 확인한다.

#### 질문에 답하는 방식

좋은 답변은 용어 정의에서 멈추지 않고 다음 순서로 이어져야 한다.

1. 어떤 데이터나 요청을 보호하려는 개념인지 말한다.
2. 그 데이터의 진실의 원천이 어느 서비스에 있는지 말한다.
3. 장애, 중복, 동시성, 지연 중 어떤 상황에서 문제가 생기는지 설명한다.
4. 코드 레벨의 방어 수단과 운영 레벨의 확인 수단을 함께 말한다.

#### PayFlow 예시 답변

```text
Optimistic And Pessimistic Locking 개념은 PayFlow에서 데이터 충돌 가능성에 맞춰 낙관적 락과 비관적 락을 선택하기 위해 필요하다.
이 개념이 없으면 충돌이 잦은 지갑 잔액에 낙관적 락만 쓰고 재시도 정책이 없으면 송금 실패가 급증할 수 있다.
그래서 코드에서는 잔액 변경에는 비관적 락이나 조건부 업데이트를, 낮은 충돌 데이터에는 @Version을 적용하고,
운영에서는 OptimisticLockException, lock wait, 재시도 성공률을 확인해야 한다.
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
Optimistic And Pessimistic Locking 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.

