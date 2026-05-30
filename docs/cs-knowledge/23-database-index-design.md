# Database Index Design

## 핵심 개념

### 인덱스의 동작 원리

인덱스는 DB가 데이터를 빠르게 찾기 위한 자료구조다. MySQL InnoDB에서는 B-Tree 인덱스가 기본이다.

B-Tree 인덱스는 루트 노드, 브랜치 노드, 리프 노드로 구성된다. 리프 노드에는 인덱스 컬럼 값과 해당 행의 Primary Key가 저장된다. 조회 시 O(log N) 시간 복잡도로 탐색이 가능하다.

```text
           [루트: 5000]
          /             \
    [1000~4999]    [5000~9999]
    /       \          /      \
[1~999]  [1000~1999]  ...   [9000~9999]
  |
[실제 데이터 또는 PK]
```

인덱스가 없으면 조건에 맞는 행을 찾기 위해 테이블 전체를 스캔(Full Table Scan)해야 한다. 데이터가 많을수록 차이가 커진다.

### Clustered Index vs Secondary Index

**Clustered Index (Primary Key)**: InnoDB에서 테이블의 실제 데이터는 Primary Key 순서로 저장된다. Primary Key로 조회하면 B-Tree를 한 번만 탐색해 데이터를 바로 찾는다.

**Secondary Index**: Primary Key가 아닌 컬럼에 만드는 인덱스다. 리프 노드에 해당 컬럼 값과 Primary Key가 저장된다. Secondary Index로 조회하면 인덱스 탐색 후 Primary Key로 다시 Clustered Index를 탐색하는 두 번의 B-Tree 탐색이 필요하다.

**Covering Index**: 쿼리에 필요한 모든 컬럼이 인덱스 안에 있으면 Clustered Index를 추가 탐색하지 않아도 된다. `EXPLAIN`에서 `Extra: Using index`로 확인할 수 있다.

```sql
-- idx_transfer_user_status (user_id, status) 인덱스가 있을 때
-- id, user_id, status 컬럼만 필요하면 Covering Index로 처리 가능
SELECT id, user_id, status FROM transfer WHERE user_id = 1 AND status = 'PENDING';
```

### 복합 인덱스 설계 원칙

복합 인덱스는 여러 컬럼을 묶어 하나의 인덱스로 만드는 것이다. 컬럼 순서가 매우 중요하다.

**등치 조건 컬럼을 앞에**: 범위 조건(`BETWEEN`, `>`, `<`, `LIKE 'prefix%'`) 컬럼 뒤에 오는 컬럼은 인덱스를 사용하지 못한다.

```sql
-- 인덱스: (status, created_at)
SELECT * FROM outbox_event WHERE status = 'PENDING' AND created_at < '2024-01-01';
-- status 등치 조건이 앞에 있으므로 두 컬럼 모두 인덱스 활용 가능

-- 인덱스: (created_at, status)
SELECT * FROM outbox_event WHERE status = 'PENDING' AND created_at < '2024-01-01';
-- created_at이 범위 조건이면 status는 인덱스 활용 불가
```

**카디널리티가 높은 컬럼을 앞에**: 카디널리티가 높을수록 인덱스의 선택도가 좋아진다. user_id(수천만)가 status(몇 가지)보다 카디널리티가 높다.

**정렬에 사용되는 컬럼 포함**: `ORDER BY` 컬럼이 인덱스에 있으면 별도 정렬 작업(`filesort`)이 필요 없어진다.

### 인덱스 설계 시 고려할 점

**선택도(Selectivity)**: 특정 값으로 필터링했을 때 전체 행 대비 얼마나 많은 행이 남는지다. 선택도가 낮으면(성별처럼 2~3가지 값) 인덱스 효과가 작다. DB 옵티마이저가 Full Table Scan이 더 빠르다고 판단할 수 있다.

**인덱스 쓰기 비용**: 인덱스는 쓰기(INSERT, UPDATE, DELETE) 시 B-Tree를 재조정해야 해 쓰기 성능이 낮아진다. 인덱스가 많을수록 쓰기 비용이 커진다. 쓰기가 많은 outbox_event 테이블은 인덱스 수를 최소화해야 한다.

**인덱스 스킵**: 복합 인덱스 `(a, b, c)`에서 `a`를 건너뛰고 `b`, `c`로만 조회하면 인덱스를 사용하지 못한다. 단, MySQL 8.0부터 Index Skip Scan이 지원되어 일부 경우에 활용 가능하다.

### Unique Index와 비즈니스 제약

Unique Index는 성능 도구이면서 동시에 비즈니스 불변식을 DB 수준에서 보장하는 안전장치다.

**멱등성 키(Idempotency Key)**: 같은 요청이 중복 처리되지 않도록 Unique Index로 강제한다. 애플리케이션 코드에서 중복 체크를 해도 동시 요청 사이에서는 Race Condition이 생길 수 있다. DB Unique Index는 원자적으로 보장된다.

```sql
CREATE TABLE transfer_idempotency (
    idempotency_key VARCHAR(64) NOT NULL,
    transfer_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_idempotency_key (idempotency_key)
);
```

동시에 같은 idempotency_key로 두 요청이 들어오면 하나는 Duplicate Entry 오류로 실패하고 하나만 성공한다.

### Partial Index와 함수 기반 Index

**Partial Index (조건부 인덱스)**: 특정 조건을 만족하는 행에만 인덱스를 만든다. MySQL에서는 직접 지원하지 않지만, `status` 컬럼을 앞에 두는 복합 인덱스로 유사한 효과를 낼 수 있다.

```sql
-- outbox_event에서 PENDING 상태만 자주 조회한다면
CREATE INDEX idx_outbox_pending ON outbox_event(status, created_at);
-- status = 'PENDING'은 소수의 행이므로 인덱스 효율 좋음
```

**함수 기반 Index**: MySQL 8.0부터 지원. 컬럼에 함수를 적용한 결과에 인덱스를 만들 수 있다. `DATE(created_at)`처럼 변환된 값으로 자주 조회할 때 유용하다.

### 흔한 오해와 함정

**"인덱스가 있으면 무조건 빠르다"**: DB 옵티마이저가 인덱스보다 Full Table Scan이 낫다고 판단하면 인덱스를 사용하지 않을 수 있다. 전체 데이터의 20~30% 이상을 반환하는 조회는 Full Scan이 더 빠를 수 있다.

**"LIKE 검색에는 인덱스가 효과 없다"**: `LIKE 'prefix%'`는 인덱스를 활용할 수 있다. 단, `LIKE '%suffix'`나 `LIKE '%middle%'`는 인덱스를 활용하지 못한다.

**"NULL은 인덱스에 저장되지 않는다"**: MySQL InnoDB는 NULL 값도 인덱스에 저장한다. `IS NULL` 조건도 인덱스를 활용할 수 있다.

**"복합 인덱스는 단일 인덱스 여러 개와 같다"**: 다르다. `(a, b)` 복합 인덱스는 `a` 단독 조회, `a + b` 조합 조회를 지원하지만 `b` 단독 조회는 지원하지 못한다.


## PayFlow 연결

PayFlow에서는 다음 컬럼에 인덱스가 중요하다.

- 사용자 ID로 지갑 조회
- 송금 ID로 송금 상태 조회
- Idempotency-Key로 중복 요청 조회
- transferId로 원장 기록 조회
- outbox_event의 발행 상태 조회
- 정산 기준일 조회

특히 멱등성 키는 중복 처리를 막기 위해 유니크 제약조건과 함께 고려해야 한다.

## 실무 포인트

- 자주 조회하는 조건에 인덱스를 둔다.
- 유니크해야 하는 비즈니스 키는 DB 제약조건으로 보장한다.
- 복합 인덱스는 컬럼 순서가 중요하다.
- 인덱스가 많으면 insert, update 비용이 증가한다.
- 실제 실행 계획으로 인덱스 사용 여부를 확인한다.

## 체크 질문

- 인덱스는 왜 쓰기 성능을 낮출 수 있는가
- Idempotency-Key에 유니크 제약이 필요한 이유는 무엇인가
- 복합 인덱스에서 컬럼 순서가 중요한 이유는 무엇인가

## 실무 설계 보강

### 대표 장애 시나리오

Idempotency-Key나 outbox status 조회가 full scan이 되어 송금 처리 시간이 늘어난다.

### 잘못된 구현 예시

~~~text
조회 조건이 있으니 DB가 알아서 빠르게 찾을 것이라고 생각한다.
~~~

### 좋은 구현 예시

~~~text
비즈니스 키 unique index와 outbox status/created_at 복합 인덱스를 설계한다.
~~~

### 대안과 선택 이유

애플리케이션 코드에서만 검증하는 방식도 있지만, 동시 요청과 장애 상황에서는 코드 검증만으로 부족하다. PayFlow는 DB 트랜잭션, 락, 유니크 제약, 인덱스, 실행 계획을 함께 사용해 데이터 계층에서도 불변식을 지키는 쪽이 더 안전하다.

### PayFlow에서 확인할 위치

transfer idempotency table, outbox_event table, migration scripts

### 면접에서 설명하기

인덱스는 성능 도구이면서 비즈니스 제약을 DB에서 보장하는 안전장치다.

### 관련 문서

24, 40, 59

## 질문의 질문으로 더 깊게 보기

### 이 문서에서 잡아야 할 CS 감각

이 구간의 핵심은 데이터가 동시에 바뀔 때 어떤 불변식을 지켜야 하는가다. 결제 시스템에서는 "잔액은 음수가 되면 안 된다", "같은 요청은 한 번만 반영되어야 한다" 같은 규칙이 코드보다 더 중요하다.

### 질문의 질문

아래 질문들은 답을 외우기 위한 질문이 아니다. 하나씩 답하면서 "왜 그런 설계가 필요한지"를 설명하는 연습을 하기 위한 질문이다.

- Database Index Design 개념이 없다면 PayFlow에서 가장 먼저 어떤 장애가 생기는가?
- 이 개념은 정확성, 성능, 보안, 운영성 중 무엇을 가장 크게 개선하는가?
- 반대로 이 개념을 잘못 적용하면 어떤 복잡도가 추가되는가?
- 이 개념을 적용하면 어떤 문제가 해결되지만, 어떤 비용이나 복잡도가 새로 생기는가?
- 장애가 발생했을 때 이 개념은 문제를 예방하는가, 감지하는가, 복구하는가?
- PayFlow 코드에서 이 개념을 확인하려면 어떤 서비스, 테이블, 로그, 테스트를 봐야 하는가?

### 답안 예시

<details>
<summary>답안 예시 펼치기</summary>

#### 핵심 답변

Database Index Design 개념은 PayFlow에서 다음 이유로 중요하다.

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
Database Index Design 개념은 PayFlow에서 멱등성 키, 송금 상태, Outbox 조회가 대용량에서도 빠르게 동작하게 하기 위해 필요하다.
이 개념이 없으면 Idempotency-Key 조회나 미발행 Outbox 조회가 full scan으로 바뀌어 송금 처리 전체가 느려진다.
그래서 코드에서는 비즈니스 키 유니크 제약과 status/created_at 복합 인덱스를 설계하고,
운영에서는 실행 계획, rows 추정치, slow query, 인덱스 사용 여부를 확인해야 한다.
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
Database Index Design 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.
