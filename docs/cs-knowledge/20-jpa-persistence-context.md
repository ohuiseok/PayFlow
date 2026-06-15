# JPA Persistence Context

## 핵심 개념

### 영속성 컨텍스트란 무엇인가

JPA 영속성 컨텍스트(Persistence Context)는 Entity 객체를 메모리에 보관하고 DB와의 동기화를 관리하는 공간이다. EntityManager가 관리하며, Spring에서는 기본적으로 트랜잭션 단위로 생성되고 종료된다.

영속성 컨텍스트가 제공하는 기능은 크게 네 가지다.

**1차 캐시**: 같은 트랜잭션 안에서 같은 PK로 Entity를 조회하면 DB를 다시 조회하지 않고 1차 캐시에서 반환한다. 이는 메모리 내 캐시이므로 트랜잭션이 끝나면 사라진다.

```java
Wallet w1 = walletRepository.findById(1L).get();  // DB 조회
Wallet w2 = walletRepository.findById(1L).get();  // 1차 캐시 반환, DB 조회 없음
assertSame(w1, w2);  // 동일 객체
```

**변경 감지(Dirty Checking)**: 트랜잭션 커밋 시점에 영속 상태 Entity의 현재 값과 스냅샷(최초 조회 시 저장)을 비교해 변경된 항목을 자동으로 `UPDATE`한다. 명시적으로 `save()`를 호출할 필요가 없다.

```java
@Transactional
public void updateBalance(long walletId, BigDecimal newBalance) {
    Wallet wallet = walletRepository.findById(walletId).orElseThrow();
    wallet.setBalance(newBalance);  // save() 호출 없이도
    // 트랜잭션 종료 시 변경 감지로 UPDATE 쿼리 자동 실행
}
```

**지연 로딩(Lazy Loading)**: 연관 Entity를 처음 접근할 때 DB를 조회한다. 트랜잭션 안에서는 동작하지만 트랜잭션 밖에서 접근하면 `LazyInitializationException`이 발생한다.

**동일성 보장**: 같은 트랜잭션 안에서 같은 PK의 Entity는 항상 동일한 객체(`==`)다.

### Entity의 생명주기

```text
비영속(new/transient)
  -> em.persist() -> 영속(managed)
                      -> 트랜잭션 커밋 -> flush -> DB 반영
  -> em.detach()  -> 준영속(detached)
  -> em.remove()  -> 삭제(removed)
```

**영속 상태**: EntityManager가 관리하는 상태. 변경 감지 대상이다.
**준영속 상태**: EntityManager에서 분리된 상태. 변경해도 DB에 반영되지 않는다. `@Transactional` 밖에서 Entity를 반환한 뒤 변경하는 경우가 이에 해당한다.
**비영속 상태**: `new`로 생성했지만 아직 persist하지 않은 상태.

### flush와 commit의 차이

`flush()`는 영속성 컨텍스트의 변경 내용을 DB에 SQL로 전송하는 것이다. 아직 트랜잭션이 커밋된 것은 아니므로 다른 트랜잭션은 변경을 볼 수 없다. `commit()`이 되어야 변경이 확정된다.

flush는 자동으로 발생하는 시점이 있다.
- 트랜잭션 커밋 직전
- JPQL 쿼리 실행 직전 (영속성 컨텍스트와 DB 불일치 방지)
- 명시적으로 `em.flush()` 호출 시

### 벌크 쿼리와 영속성 컨텍스트 불일치

JPQL 벌크 쿼리(`UPDATE`, `DELETE`)는 영속성 컨텍스트를 거치지 않고 DB에 직접 실행된다. 따라서 1차 캐시에 올라와 있는 Entity와 DB 상태가 달라진다.

```java
@Transactional
public void bulkDeactivate() {
    Wallet wallet = walletRepository.findById(1L).get();  // 1차 캐시에 올라옴

    // 벌크 업데이트: 영속성 컨텍스트를 거치지 않음
    walletRepository.bulkUpdateStatus("INACTIVE");

    // 주의: wallet 객체는 아직 ACTIVE 상태 (1차 캐시 기준)
    wallet.getStatus();  // ACTIVE (실제 DB는 INACTIVE)
}
```

해결책은 벌크 쿼리 후 `em.clear()`로 영속성 컨텍스트를 비우거나, `@Modifying(clearAutomatically = true)`를 사용하는 것이다.

### 대량 처리에서의 영속성 컨텍스트 관리

정산 배치처럼 수만 건의 데이터를 처리할 때 영속성 컨텍스트에 Entity가 계속 쌓이면 다음 문제가 생긴다.

- 메모리 증가 (GC 압박)
- 커밋 시 변경 감지 대상이 많아 성능 저하
- 1차 캐시가 커져 캐시 히트율이 의미없어짐

해결 방법:

```java
@Transactional
public void processBatch(List<Long> ids) {
    int batchSize = 100;
    for (int i = 0; i < ids.size(); i++) {
        Settlement s = settlementRepository.findById(ids.get(i)).orElseThrow();
        s.markProcessed();

        if (i % batchSize == 0) {
            entityManager.flush();   // DB에 변경 전송
            entityManager.clear();  // 1차 캐시 비움
        }
    }
}
```

또는 처음부터 DTO Projection으로 조회해 Entity를 영속성 컨텍스트에 올리지 않는 방법도 있다.

### DTO Projection vs Entity 조회

Entity를 영속 상태로 조회하면 변경 감지 대상이 되어 스냅샷을 메모리에 보관한다. 읽기 전용 API라면 DTO로 바로 조회하는 것이 효율적이다.

```java
// Entity 조회: 변경 감지 스냅샷 생성, 메모리 더 사용
List<Wallet> wallets = walletRepository.findAll();

// DTO 조회: 스냅샷 없음, 메모리 절약, 변경 불가
@Query("SELECT new com.payflow.dto.WalletDto(w.id, w.balance) FROM Wallet w")
List<WalletDto> findAllAsDto();
```

### 흔한 오해와 함정

**"save()를 안 불러도 저장된다"**: 변경 감지는 편리하지만 의도치 않은 UPDATE가 발생할 수 있다. Entity를 조회한 뒤 값을 변경하면 명시적 save 없이도 UPDATE가 실행된다.

**"영속성 컨텍스트는 2차 캐시와 같다"**: 1차 캐시는 트랜잭션 단위, 2차 캐시는 SessionFactory 단위로 애플리케이션 전체에서 공유한다. JPA 기본은 1차 캐시만 있고, 2차 캐시는 별도 설정이 필요하다.

**"OSIV(Open Session In View)가 있으면 지연 로딩 문제가 없다"**: OSIV를 켜면 뷰 렌더링까지 트랜잭션이 열려있어 지연 로딩이 가능하지만, 커넥션을 오래 점유하고 예상치 못한 쿼리가 발생할 수 있다. 운영 환경에서는 OSIV를 끄고 명시적으로 필요한 데이터를 조회하는 것이 권장된다.


## PayFlow 연결

`wallet-service`에서 지갑 Entity를 조회한 뒤 잔액을 변경하면 JPA 변경 감지로 update가 발생할 수 있다. 이때 트랜잭션 범위 안에서 Entity 상태가 어떻게 관리되는지 이해해야 한다.

정산 배치처럼 많은 데이터를 처리할 때 영속성 컨텍스트에 Entity가 계속 쌓이면 메모리 문제가 생길 수 있다.

## 실무 포인트

- 변경 감지는 트랜잭션 안에서 동작한다.
- 대량 처리에서는 주기적으로 flush, clear를 고려한다.
- 지연 로딩은 트랜잭션 밖에서 예외를 만들 수 있다.
- Entity와 DTO를 구분한다.
- 벌크 업데이트는 영속성 컨텍스트와 DB 상태 불일치를 만들 수 있다.

## 체크 질문

- JPA 1차 캐시는 무엇인가
- 변경 감지는 언제 DB에 반영되는가
- 배치 처리에서 영속성 컨텍스트를 비워야 하는 이유는 무엇인가

## 실무 설계 참고

### 대표 장애 시나리오

정산 배치에서 수천 개 엔티티가 영속성 컨텍스트에 쌓여 메모리가 증가한다.

### 잘못된 구현 예시

~~~text
JPA가 알아서 DB와 객체 상태를 항상 최신으로 맞춘다고 생각한다.
~~~

### 좋은 구현 예시

~~~text
대량 처리는 DTO 조회, flush/clear, 벌크 쿼리 후 컨텍스트 정리를 적용한다.
~~~

### 대안과 선택 이유

애플리케이션 코드에서만 검증하는 방식도 있지만, 동시 요청과 장애 상황에서는 코드 검증만으로 부족하다. PayFlow는 DB 트랜잭션, 락, 유니크 제약, 인덱스, 실행 계획을 함께 사용해 데이터 계층에서도 불변식을 지키는 쪽이 더 안전하다.

### PayFlow에서 확인할 위치

JPA repository, settlement-service batch, entity mapping

### 면접에서 설명하기

영속성 컨텍스트는 편리한 1차 캐시지만 대량 처리와 벌크 업데이트에서는 명시적 관리가 필요하다.

### 관련 문서

13, 21, 48

## 질문의 질문으로 더 깊게 보기

### 이 문서에서 잡아야 할 CS 감각

이 구간의 핵심은 데이터가 동시에 바뀔 때 어떤 불변식을 지켜야 하는가다. 결제 시스템에서는 "잔액은 음수가 되면 안 된다", "같은 요청은 한 번만 반영되어야 한다" 같은 규칙이 코드보다 더 중요하다.

### 질문의 질문

아래 질문들은 답을 외우기 위한 질문이 아니다. 하나씩 답하면서 "왜 그런 설계가 필요한지"를 설명하는 연습을 하기 위한 질문이다.

- JPA Persistence Context 개념이 없다면 PayFlow에서 가장 먼저 어떤 장애가 생기는가?
- 이 개념은 정확성, 성능, 보안, 운영성 중 무엇을 가장 크게 개선하는가?
- 반대로 이 개념을 잘못 적용하면 어떤 복잡도가 추가되는가?
- 이 개념을 적용하면 어떤 문제가 해결되지만, 어떤 비용이나 복잡도가 새로 생기는가?
- 장애가 발생했을 때 이 개념은 문제를 예방하는가, 감지하는가, 복구하는가?
- PayFlow 코드에서 이 개념을 확인하려면 어떤 서비스, 테이블, 로그, 테스트를 봐야 하는가?

### 답안 예시

<details>
<summary>답안 예시 펼치기</summary>

#### 핵심 답변

JPA Persistence Context 개념은 PayFlow에서 다음 이유로 중요하다.

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
JPA Persistence Context 개념은 PayFlow에서 JPA 변경 감지와 1차 캐시가 잔액 변경과 배치 처리에서 예상대로 동작하게 하기 위해 필요하다.
이 개념이 없으면 영속성 컨텍스트에 오래된 엔티티가 남아 벌크 업데이트 후 잘못된 잔액을 기준으로 처리할 수 있다.
그래서 코드에서는 트랜잭션 범위를 명확히 하고 배치에서는 flush/clear와 DTO 조회를 사용하며,
운영에서는 예상치 못한 update 쿼리, 메모리 증가, LazyInitializationException을 확인해야 한다.
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
JPA Persistence Context 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.
