# Spring Transaction Propagation

## 핵심 개념

### Spring 트랜잭션의 동작 원리

Spring의 `@Transactional`은 AOP 프록시를 기반으로 동작한다. 빈(Bean)을 주입받을 때 실제 객체가 아닌 프록시 객체가 주입되고, 메서드 호출 시 프록시가 트랜잭션을 시작하거나 참여한 뒤 실제 메서드를 호출한다. 메서드가 정상 반환되면 커밋, 런타임 예외가 발생하면 롤백한다.

```text
Client -> [Proxy: 트랜잭션 시작] -> 실제 Service 메서드 -> [Proxy: 커밋/롤백]
```

프록시 기반이므로 같은 클래스 내부에서 자기 자신의 메서드를 호출하면(self-invocation) 프록시를 거치지 않아 `@Transactional`이 동작하지 않는다.

### 전파 옵션(Propagation) 종류와 의미

전파 옵션은 "이미 트랜잭션이 있는 상태에서 다른 트랜잭션 메서드를 호출할 때 어떻게 동작할지" 정한다.

**REQUIRED (기본값)**: 기존 트랜잭션이 있으면 참여하고, 없으면 새로 만든다. 대부분의 서비스 메서드에 적합하다. 주의할 점은 내부 메서드가 롤백을 표시(rollback-only)하면 외부 트랜잭션도 롤백된다는 것이다.

```java
@Transactional  // REQUIRED (기본값)
public void transfer(TransferRequest request) {
    validateBalance();      // 같은 트랜잭션 참여
    deductBalance();        // 같은 트랜잭션 참여
    saveOutboxEvent();      // 같은 트랜잭션 참여 -> 원자적 커밋
}
```

**REQUIRES_NEW**: 기존 트랜잭션을 일시 중단하고 새 트랜잭션을 시작한다. 내부 트랜잭션의 커밋/롤백이 외부 트랜잭션에 영향을 주지 않는다.

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void saveAuditLog(String event) {
    // 외부 트랜잭션이 롤백되어도 이 로그는 남는다
    auditRepository.save(new AuditLog(event));
}
```

**NESTED**: 외부 트랜잭션 안에 중첩된 트랜잭션을 만든다. 내부 트랜잭션만 롤백(Savepoint 사용)할 수 있다. 외부 트랜잭션이 롤백되면 내부도 같이 롤백된다. JPA에서는 Savepoint 지원이 제한적이라 잘 쓰이지 않는다.

**SUPPORTS**: 기존 트랜잭션이 있으면 참여하고, 없으면 트랜잭션 없이 실행한다. 읽기 전용 메서드에 적합할 수 있다.

**MANDATORY**: 기존 트랜잭션이 없으면 예외를 던진다. 반드시 트랜잭션 안에서 호출해야 하는 메서드에 사용한다.

**NOT_SUPPORTED**: 기존 트랜잭션을 일시 중단하고 트랜잭션 없이 실행한다.

**NEVER**: 기존 트랜잭션이 있으면 예외를 던진다.

### 롤백 규칙

기본적으로 `RuntimeException`과 `Error`가 발생하면 롤백, `CheckedException`은 롤백하지 않는다. `rollbackFor`나 `noRollbackFor`로 커스텀할 수 있다.

```java
@Transactional(rollbackFor = Exception.class)  // 모든 예외에서 롤백
@Transactional(noRollbackFor = BusinessException.class)  // 비즈니스 예외는 롤백 안 함
```

### Outbox와 전파 옵션의 관계

PayFlow 같은 시스템에서 Outbox 패턴을 사용할 때 전파 옵션 선택이 핵심이다.

**잘못된 패턴 - REQUIRES_NEW로 Outbox 저장**:
```text
transfer 저장 (TX1 시작)
Outbox 저장 (TX2 시작, 커밋) <- 이미 커밋됨
transfer 저장 롤백 (TX1 롤백)
결과: transfer는 없는데 outbox 이벤트는 남아있음
```

**올바른 패턴 - REQUIRED로 같은 트랜잭션에 묶기**:
```text
transfer 저장 + Outbox 저장 (같은 TX)
함께 커밋 또는 함께 롤백
결과: transfer와 outbox는 항상 같은 상태
```

감사 로그나 실패 기록은 본 트랜잭션이 롤백되어도 남겨야 하므로 `REQUIRES_NEW`가 적합하다.

### Self-invocation 문제

```java
@Service
public class WalletService {

    @Transactional
    public void withdraw(long walletId, BigDecimal amount) {
        // 내부에서 자신의 메서드를 직접 호출
        this.saveHistory(walletId, amount);  // 프록시를 거치지 않아 @Transactional 무시됨
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveHistory(long walletId, BigDecimal amount) {
        // 의도: 별도 트랜잭션으로 저장하려 했지만 실제로는 같은 트랜잭션에서 동작
    }
}
```

해결책은 `saveHistory`를 별도 Bean으로 분리하거나, ApplicationContext에서 자기 자신을 다시 주입받아 호출하는 것이다.

### 읽기 전용 트랜잭션

`@Transactional(readOnly = true)`는 변경 감지(Dirty Checking)를 비활성화하고 DB에 읽기 전용 힌트를 전달한다. 성능 최적화와 실수 방지 효과가 있다. 하지만 읽기 복제본(Replica DB)으로 라우팅하는 것은 별도 설정이 필요하다.

### 흔한 오해와 함정

**"REQUIRED면 항상 안전하다"**: REQUIRED는 기존 트랜잭션에 참여하므로, 내부 메서드가 `rollback-only`로 표시되면 외부 트랜잭션도 롤백된다. 예외를 잡아서 처리하려 해도 `UnexpectedRollbackException`이 발생할 수 있다.

**"CheckedException은 롤백 안 된다"**: 기본값이 그렇지만, 비즈니스 로직에 맞게 `rollbackFor`를 설정해야 한다. 잔액 부족 예외가 CheckedException이라도 롤백이 필요한 경우가 있다.

**"트랜잭션 범위 밖에서 지연 로딩이 된다"**: `@Transactional` 범위를 벗어나면 영속성 컨텍스트가 닫혀 지연 로딩이 실패한다(`LazyInitializationException`).


## PayFlow 연결

PayFlow에서 송금 처리 중 상태 저장, Outbox 저장, 이력 저장이 같은 트랜잭션에 묶여야 하는 경우가 있다. 반대로 실패 로그나 감사 로그는 본 트랜잭션이 롤백되어도 남겨야 할 수 있다. 이때 전파 옵션 선택이 중요해진다.

예를 들어 Outbox 이벤트는 송금 상태 변경과 같은 트랜잭션에 있어야 한다. 그래야 송금은 완료되었는데 이벤트가 없는 상황을 줄일 수 있다.

## 실무 포인트

- 기본값 `REQUIRED`의 의미를 정확히 이해한다.
- `REQUIRES_NEW`는 별도 커밋이 필요할 때만 사용한다.
- 자기 자신의 메서드를 호출하면 프록시 기반 트랜잭션이 적용되지 않을 수 있다.
- 읽기 전용 트랜잭션과 쓰기 트랜잭션을 구분한다.

## 체크 질문

- `REQUIRED`와 `REQUIRES_NEW`의 차이는 무엇인가
- Outbox 저장은 왜 송금 상태 저장과 같은 트랜잭션에 있어야 하는가
- Spring에서 self-invocation이 트랜잭션 문제를 만드는 이유는 무엇인가

## 실무 설계 참고

### 대표 장애 시나리오

Outbox 저장 메서드가 REQUIRES_NEW로 커밋되어 송금 상태 롤백 후 이벤트만 남는다.

### 잘못된 구현 예시

~~~text
전파 옵션을 기본값으로만 알고 호출 구조와 프록시 동작을 고려하지 않는다.
~~~

### 좋은 구현 예시

~~~text
송금 상태 변경과 outbox 저장은 같은 트랜잭션에 두고, 별도 커밋이 필요한 로그만 분리한다.
~~~

### 대안과 선택 이유

애플리케이션 코드에서만 검증하는 방식도 있지만, 동시 요청과 장애 상황에서는 코드 검증만으로 부족하다. PayFlow는 DB 트랜잭션, 락, 유니크 제약, 인덱스, 실행 계획을 함께 사용해 데이터 계층에서도 불변식을 지키는 쪽이 더 안전하다.

### PayFlow에서 확인할 위치

transfer-service service layer, transaction annotation, outbox save path

### 면접에서 설명하기

Spring 트랜잭션은 어노테이션만 붙이면 끝이 아니다. 전파 옵션이 데이터 일관성을 바꾼다.

### 관련 문서

18, 35, 59

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

Spring Transaction Propagation 개념은 PayFlow에서 다음 이유로 중요하다.

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
Spring Transaction Propagation 개념은 PayFlow에서 송금 상태 변경과 Outbox 저장이 의도한 트랜잭션 경계에 묶이게 하기 위해 필요하다.
이 개념이 없으면 REQUIRES_NEW를 잘못 써서 송금 상태는 롤백됐는데 이벤트만 커밋되는 식의 불일치가 생길 수 있다.
그래서 코드에서는 전파 옵션을 명시적으로 선택하고 self-invocation을 피하며 트랜잭션 테스트를 작성하고,
운영에서는 Outbox만 남은 건, 상태만 바뀐 건, 롤백 로그를 확인해야 한다.
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
Spring Transaction Propagation 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.
