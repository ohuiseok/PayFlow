# N Plus One Problem

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

## 핵심 개념

### N+1 문제의 발생 원리

N+1 문제는 처음 1번의 쿼리로 N개의 데이터를 조회한 뒤, 각 데이터의 연관 객체를 조회하느라 추가 쿼리 N번이 발생하는 문제다. 총 N+1번의 쿼리가 실행된다.

JPA에서 연관관계의 기본 Fetch 전략은 `@OneToMany`, `@ManyToMany`는 `LAZY`, `@ManyToOne`, `@OneToOne`은 `EAGER`다. `LAZY` 연관관계는 실제로 접근할 때 추가 쿼리가 발생한다.

```java
// 전송 목록 조회 - 1번 쿼리
List<Transfer> transfers = transferRepository.findAll();

for (Transfer transfer : transfers) {
    // 각 Transfer마다 Wallet 조회 쿼리 1번씩 발생
    String walletOwner = transfer.getFromWallet().getOwnerName();  // N번 쿼리
}
// 합계: 1 + N번 쿼리
```

### EAGER 로딩이 해결책이 아닌 이유

`EAGER`로 바꾸면 지연 로딩 대신 즉시 로딩이 되어 N+1 문제처럼 보이지 않을 수 있다. 하지만 실제로는 다음 문제가 생긴다.

- JPQL로 부모를 조회하면 JPA는 N번의 추가 SELECT를 실행해 EAGER 연관을 채운다. 근본적 해결이 아니다.
- 항상 연관 데이터를 함께 로딩해 불필요한 데이터를 가져온다.
- `@OneToMany` EAGER + 페이징 조합은 HibernateWarning을 내고 인메모리 페이징을 한다.

### 해결 방법 1: Fetch Join

JPQL에서 `JOIN FETCH`를 사용하면 연관 Entity를 한 번의 쿼리로 함께 조회한다.

```java
@Query("SELECT t FROM Transfer t JOIN FETCH t.fromWallet WHERE t.status = :status")
List<Transfer> findWithWallet(@Param("status") TransferStatus status);
```

생성되는 SQL:

```sql
SELECT t.*, w.*
FROM transfer t
INNER JOIN wallet w ON t.from_wallet_id = w.id
WHERE t.status = ?
```

**Fetch Join의 한계**: `@OneToMany` fetch join과 페이징(`LIMIT`)을 함께 사용하면 Hibernate가 메모리에서 전체를 가져온 뒤 페이징하여 메모리 문제가 발생한다.

### 해결 방법 2: @EntityGraph

`@EntityGraph`로 Fetch Join을 어노테이션으로 표현할 수 있다.

```java
@EntityGraph(attributePaths = {"fromWallet", "toWallet"})
List<Transfer> findByStatus(TransferStatus status);
```

### 해결 방법 3: DTO Projection

연관 Entity 전체가 필요하지 않고 일부 필드만 필요하면 DTO로 직접 조회한다. 영속성 컨텍스트에 Entity를 올리지 않아 메모리도 절약된다.

```java
@Query("SELECT new com.payflow.dto.TransferSummaryDto(t.id, t.amount, t.status, w.balance) FROM Transfer t JOIN t.fromWallet w WHERE t.userId = :userId")
List<TransferSummaryDto> findSummaryByUser(@Param("userId") long userId);
```

### 해결 방법 4: Batch Size

`@BatchSize`나 `spring.jpa.properties.hibernate.default_batch_fetch_size`로 IN 절 일괄 조회를 사용한다. N+1 대신 N/batchSize + 1번 쿼리가 실행된다.

```java
@BatchSize(size = 100)
@OneToMany(mappedBy = "transfer", fetch = FetchType.LAZY)
private List<LedgerEntry> entries;
```

생성되는 SQL (transfer 100개 조회 후):

```sql
-- N+1 없이 IN절로 한 번에 조회
SELECT * FROM ledger_entry WHERE transfer_id IN (1, 2, 3, ..., 100)
```

### 페이징과 컬렉션 조회 패턴

`@OneToMany` 컬렉션을 fetch join으로 가져오면서 페이징을 하려면 별도 전략이 필요하다.

```java
// 1단계: 페이징으로 부모 ID 조회
Page<Long> transferIds = transferRepository.findTransferIds(pageable);

// 2단계: ID로 연관 데이터 함께 조회
List<Transfer> transfers = transferRepository.findWithDetailsIn(transferIds.getContent());
```

또는 `@BatchSize`를 사용해 컬렉션을 IN절로 일괄 조회한다.

### N+1 문제 감지 방법

**SQL 로그 확인**: `spring.jpa.show-sql=true`, `logging.level.org.hibernate.SQL=DEBUG`로 실행 쿼리를 확인한다.

**p6spy**: 실제 SQL과 파라미터를 함께 출력하는 라이브러리다.

**쿼리 카운트 테스트**:

```java
@Test
void 목록조회_N_plus_1_방지() {
    // given: transfer 10개 준비
    // when: 목록 조회
    List<Transfer> result = transferService.findAll();
    // then: 쿼리가 1~2번 이상 실행되지 않았는지 확인
    assertThat(queryCount).isLessThanOrEqualTo(2);
}
```

### 흔한 오해와 함정

**"개발 환경에서는 괜찮은데 운영에서만 느리다"**: 개발 환경에는 데이터가 수십 건이지만 운영에서는 수만 건이라 차이가 크게 나타난다. N+1은 데이터가 적을 때 잘 보이지 않는다.

**"Fetch Join 쓰면 무조건 해결된다"**: Fetch Join은 컬렉션 페이징과 함께 쓸 때 메모리 문제를 만들고, 여러 컬렉션을 동시에 Fetch Join하면 결과가 곱집합이 된다.

**"2차 캐시가 있으면 된다"**: N+1 문제는 쿼리 수 자체가 문제이므로 캐시가 없는 첫 요청이나 캐시 미스 시 여전히 발생한다.


## PayFlow 연결

PayFlow에서 사용자와 지갑, 송금과 송금 상세, 원장과 엔트리 같은 연관 데이터를 조회할 때 N+1 문제가 생길 수 있다.

예를 들어 송금 목록 100건을 조회한 뒤 각 송금의 원장 기록을 지연 로딩하면 쿼리가 101번 발생할 수 있다.

## 실무 포인트

- 조회 API는 실행 쿼리를 확인한다.
- Fetch Join, EntityGraph, DTO Projection을 적절히 사용한다.
- 모든 연관관계를 EAGER로 바꾸는 것은 해결책이 아니다.
- 목록 조회와 상세 조회의 쿼리 전략을 분리한다.
- 페이징과 fetch join 조합을 조심한다.

## 체크 질문

- N+1 문제는 왜 발생하는가
- EAGER 로딩을 남발하면 왜 위험한가
- 송금 목록 API에서 N+1을 어떻게 확인할 수 있는가

## 실무 설계 참고

### 대표 장애 시나리오

송금 목록 100건을 조회하면서 각 원장/지갑 정보를 추가 조회해 쿼리가 폭증한다.

### 잘못된 구현 예시

~~~text
연관관계를 EAGER로 바꾸면 N+1이 해결된다고 생각한다.
~~~

### 좋은 구현 예시

~~~text
목록 조회는 DTO projection이나 fetch join 전략을 별도로 설계하고 쿼리 수를 측정한다.
~~~

### 대안과 선택 이유

애플리케이션 코드에서만 검증하는 방식도 있지만, 동시 요청과 장애 상황에서는 코드 검증만으로 부족하다. PayFlow는 DB 트랜잭션, 락, 유니크 제약, 인덱스, 실행 계획을 함께 사용해 데이터 계층에서도 불변식을 지키는 쪽이 더 안전하다.

### PayFlow에서 확인할 위치

transfer-service 목록 조회, ledger 조회, JPA SQL log

### 면접에서 설명하기

N+1은 기능 버그가 아니라 데이터가 늘어난 뒤 터지는 성능 버그다.

### 관련 문서

20, 23, 24, 79

## 질문의 질문으로 더 깊게 보기

### 이 문서에서 잡아야 할 CS 감각

이 구간의 핵심은 데이터가 동시에 바뀔 때 어떤 불변식을 지켜야 하는가다. 결제 시스템에서는 "잔액은 음수가 되면 안 된다", "같은 요청은 한 번만 반영되어야 한다" 같은 규칙이 코드보다 더 중요하다.

### 질문의 질문

아래 질문들은 답을 외우기 위한 질문이 아니다. 하나씩 답하면서 "왜 그런 설계가 필요한지"를 설명하는 연습을 하기 위한 질문이다.

- N Plus One Problem 개념이 없다면 PayFlow에서 가장 먼저 어떤 장애가 생기는가?
- 이 개념은 정확성, 성능, 보안, 운영성 중 무엇을 가장 크게 개선하는가?
- 반대로 이 개념을 잘못 적용하면 어떤 복잡도가 추가되는가?
- 이 개념을 적용하면 어떤 문제가 해결되지만, 어떤 비용이나 복잡도가 새로 생기는가?
- 장애가 발생했을 때 이 개념은 문제를 예방하는가, 감지하는가, 복구하는가?
- PayFlow 코드에서 이 개념을 확인하려면 어떤 서비스, 테이블, 로그, 테스트를 봐야 하는가?

### 답안 예시

<details>
<summary>답안 예시 펼치기</summary>

#### 핵심 답변

N Plus One Problem 개념은 PayFlow에서 다음 이유로 중요하다.

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
N Plus One Problem 개념은 PayFlow에서 송금 목록이나 원장 조회가 데이터 증가와 함께 급격히 느려지지 않게 하기 위해 필요하다.
이 개념이 없으면 송금 100건 조회 후 각 원장 엔트리를 지연 로딩해 쿼리가 101번 발생할 수 있다.
그래서 코드에서는 fetch join, EntityGraph, DTO projection, 목록/상세 조회 분리를 적용하고,
운영에서는 쿼리 수, slow query, DB CPU, p95 조회 시간을 확인해야 한다.
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
N Plus One Problem 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.

