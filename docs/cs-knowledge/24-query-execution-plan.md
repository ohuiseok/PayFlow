# Query Execution Plan

## 핵심 개념

### 쿼리 실행 계획이란

쿼리 실행 계획은 DB 옵티마이저가 SQL을 어떻게 실행할지 결정한 계획이다. 옵티마이저는 통계 정보(테이블 행 수, 인덱스 카디널리티, 컬럼 분포)를 바탕으로 여러 실행 경로 중 비용이 가장 낮은 것을 선택한다.

MySQL에서는 `EXPLAIN` 또는 `EXPLAIN ANALYZE`로 실행 계획을 확인할 수 있다.

```sql
EXPLAIN SELECT * FROM transfer WHERE user_id = 1 AND status = 'PENDING';
EXPLAIN ANALYZE SELECT * FROM transfer WHERE user_id = 1 AND status = 'PENDING';
```

`EXPLAIN`은 예상 계획을 보여주고, `EXPLAIN ANALYZE`는 실제로 쿼리를 실행하며 실측 통계를 함께 보여준다.

### EXPLAIN 결과 컬럼 해석

| 컬럼 | 의미 |
|---|---|
| `id` | 쿼리 블록 번호. 서브쿼리가 있으면 여러 행이 나온다 |
| `select_type` | 단순 SELECT, 서브쿼리, UNION 등 쿼리 유형 |
| `table` | 어떤 테이블을 읽는지 |
| `type` | 접근 방식. 성능 지표의 핵심 |
| `possible_keys` | 사용 가능한 인덱스 목록 |
| `key` | 실제로 선택된 인덱스 |
| `key_len` | 사용된 인덱스 길이 (복합 인덱스에서 몇 컬럼 활용했는지 유추) |
| `rows` | 예상 검색 행 수. 실제와 차이가 있을 수 있다 |
| `filtered` | rows 중 WHERE 조건 통과 예상 비율(%) |
| `Extra` | 추가 정보. 성능에 영향을 주는 힌트 |

### type 컬럼: 성능의 핵심 지표

`type`은 테이블 접근 방식을 나타낸다. 성능이 좋은 순서는 다음과 같다.

```text
system > const > eq_ref > ref > range > index > ALL
```

- **system/const**: Primary Key나 Unique Key로 1개 행을 조회. 가장 빠르다.
- **eq_ref**: JOIN에서 드리블 테이블을 PK나 Unique Key로 1개씩 조회.
- **ref**: Non-Unique 인덱스로 동등 조건 조회. 여러 행이 나올 수 있다.
- **range**: 인덱스 범위 스캔. `BETWEEN`, `>`, `<`, `IN` 등.
- **index**: 인덱스 Full Scan. 인덱스 전체를 읽지만 데이터 파일보다는 빠르다.
- **ALL**: Full Table Scan. 최악의 경우. 대용량 테이블에서는 병목이 된다.

결제 시스템에서 잔액 조회, 송금 상태 조회, Outbox 조회가 `ALL`이면 즉시 인덱스 설계를 검토해야 한다.

### Extra 컬럼: 추가 동작 신호

| Extra 값 | 의미 |
|---|---|
| `Using index` | Covering Index 사용. 추가 데이터 파일 접근 없음 |
| `Using where` | 인덱스 조회 후 추가 WHERE 필터링 적용 |
| `Using filesort` | 인덱스를 사용하지 못해 별도 정렬 작업 필요. 대용량에서 느리다 |
| `Using temporary` | 임시 테이블 사용. GROUP BY, ORDER BY 등에서 발생 |
| `Using index condition` | Index Condition Pushdown. 인덱스 단계에서 일부 조건 필터링 |
| `Using join buffer` | JOIN에서 인덱스 없이 버퍼를 사용한 조인 |

`Using filesort`와 `Using temporary`가 대용량 테이블에서 발생하면 인덱스와 쿼리를 같이 검토해야 한다.

### 실행 계획 분석 실습: Outbox 쿼리 예시

```sql
-- Outbox Publisher가 미발행 이벤트를 주기적으로 조회하는 쿼리
EXPLAIN
SELECT id, event_type, payload, created_at
FROM outbox_event
WHERE status = 'PENDING'
ORDER BY created_at ASC
LIMIT 100;
```

**인덱스 없는 경우**:
```
type: ALL, key: NULL, rows: 100000, Extra: Using where; Using filesort
```
전체 10만 행을 스캔하고 정렬까지 한다. Outbox Publisher가 1분에 한 번 실행되면 DB에 큰 부하가 된다.

**복합 인덱스 (status, created_at) 추가 후**:
```
type: range, key: idx_outbox_status_created, rows: 50, Extra: Using index condition
```
status 조건으로 좁힌 후 created_at 순서로 정렬된 인덱스를 활용해 filesort가 없어진다.

### rows와 실제 결과의 차이

옵티마이저의 `rows`는 통계 정보를 바탕으로 한 **추정값**이다. 실제 결과와 크게 다를 수 있다. 특히 다음 상황에서 차이가 크다.

- 데이터가 균일하지 않을 때 (특정 status 값에 데이터가 몰릴 때)
- 통계가 오래됐을 때 (`ANALYZE TABLE`로 갱신 가능)
- 서브쿼리나 복잡한 조인에서

`EXPLAIN ANALYZE`를 사용하면 실제 실행 결과(`actual rows`, `actual time`)를 확인할 수 있다.

### 인덱스 힌트와 옵티마이저 제어

옵티마이저가 잘못된 인덱스를 선택할 경우 힌트로 강제할 수 있다.

```sql
-- 특정 인덱스 강제 사용
SELECT * FROM transfer USE INDEX (idx_transfer_user_status) WHERE user_id = 1;

-- 특정 인덱스 제외
SELECT * FROM transfer IGNORE INDEX (idx_transfer_created_at) WHERE user_id = 1;
```

단, 인덱스 힌트는 코드에 박아두면 나중에 인덱스 변경 시 힌트도 수정해야 하므로 최후 수단으로 사용한다.

### 페이징과 실행 계획

`LIMIT offset, count` 방식의 페이징은 offset이 커질수록 성능이 떨어진다.

```sql
-- offset이 클수록 느려짐 (offset만큼 건너뛰기 위해 스캔)
SELECT * FROM transfer ORDER BY created_at LIMIT 90000, 10;
```

커서 기반 페이징(No-Offset)을 사용하면 항상 비슷한 성능을 유지한다.

```sql
-- 마지막으로 받은 created_at, id를 기억하고 다음 페이지 요청
SELECT * FROM transfer
WHERE (created_at, id) < (?, ?)
ORDER BY created_at DESC, id DESC
LIMIT 10;
```

### 흔한 오해와 함정

**"EXPLAIN이 보여주는 게 실제 실행이다"**: EXPLAIN은 예상 계획이다. 통계 오차, 파라미터 바인딩 시 값 변화 등으로 실제 실행이 달라질 수 있다. `EXPLAIN ANALYZE`로 실측값을 확인해야 한다.

**"rows가 작으면 항상 빠르다"**: rows는 인덱스 단계의 추정치다. 이후 추가 필터링이나 정렬이 있으면 전체 비용은 달라진다.

**"느린 쿼리는 인덱스만 추가하면 된다"**: 쿼리 자체가 비효율적이거나(불필요한 함수 적용, 와일드카드 전처리 LIKE, 묵시적 형변환) 인덱스 통계가 잘못됐을 수도 있다. 쿼리와 인덱스를 함께 검토해야 한다.

**"묵시적 형변환을 몰라서 인덱스 못 탄다"**: 컬럼이 `VARCHAR`인데 숫자로 조회하거나, `DATE`인데 문자열로 비교하면 형변환이 일어나 인덱스를 사용하지 못한다.

```sql
-- wallet_id가 BIGINT인데 문자열로 조회 -> 형변환 발생, 인덱스 사용 불가
SELECT * FROM wallet WHERE wallet_id = '12345';

-- 올바른 방식
SELECT * FROM wallet WHERE wallet_id = 12345;
```


## PayFlow 연결

송금 목록 조회, 지갑 거래 내역 조회, 원장 조회, 정산 대상 조회는 데이터가 늘어날수록 느려질 수 있다. 이때 단순히 인덱스를 추가하기보다 실행 계획을 보고 병목을 찾아야 한다.

Outbox Publisher가 미발행 이벤트를 반복 조회한다면, `status`, `created_at` 같은 조건에 적절한 인덱스가 없을 경우 전체 테이블 스캔이 발생할 수 있다.

## 실무 포인트

- 느린 쿼리는 `EXPLAIN`으로 확인한다.
- Full Table Scan이 항상 나쁜 것은 아니지만 대용량에서는 위험하다.
- rows 추정치와 실제 결과 차이를 본다.
- 정렬과 페이징에서 인덱스 사용 여부를 확인한다.
- 인덱스 추가 후 쓰기 성능 영향도 고려한다.

## 체크 질문

- 실행 계획을 보는 이유는 무엇인가
- Outbox 조회 쿼리에 인덱스가 없으면 어떤 문제가 생기는가
- Full Table Scan은 항상 나쁜가

## 실무 설계 보강

### 대표 장애 시나리오

정산 대상 조회가 느려졌는데 원인을 모르고 서버만 증설한다.

### 잘못된 구현 예시

~~~text
SQL이 느리면 일단 인덱스를 아무거나 추가한다.
~~~

### 좋은 구현 예시

~~~text
EXPLAIN으로 rows, type, key, filesort를 보고 쿼리와 인덱스를 같이 조정한다.
~~~

### 대안과 선택 이유

애플리케이션 코드에서만 검증하는 방식도 있지만, 동시 요청과 장애 상황에서는 코드 검증만으로 부족하다. PayFlow는 DB 트랜잭션, 락, 유니크 제약, 인덱스, 실행 계획을 함께 사용해 데이터 계층에서도 불변식을 지키는 쪽이 더 안전하다.

### PayFlow에서 확인할 위치

settlement query, outbox publisher query, MySQL EXPLAIN 결과

### 면접에서 설명하기

실행 계획은 DB가 실제로 어떤 길로 데이터를 찾는지 보여주는 지도다.

### 관련 문서

21, 23, 48, 79

## 질문의 질문으로 더 깊게 보기

### 이 문서에서 잡아야 할 CS 감각

이 구간의 핵심은 데이터가 동시에 바뀔 때 어떤 불변식을 지켜야 하는가다. 결제 시스템에서는 "잔액은 음수가 되면 안 된다", "같은 요청은 한 번만 반영되어야 한다" 같은 규칙이 코드보다 더 중요하다.

### 질문의 질문

아래 질문들은 답을 외우기 위한 질문이 아니다. 하나씩 답하면서 "왜 그런 설계가 필요한지"를 설명하는 연습을 하기 위한 질문이다.

- Query Execution Plan 개념이 없다면 PayFlow에서 가장 먼저 어떤 장애가 생기는가?
- 이 개념은 정확성, 성능, 보안, 운영성 중 무엇을 가장 크게 개선하는가?
- 반대로 이 개념을 잘못 적용하면 어떤 복잡도가 추가되는가?
- 이 개념을 적용하면 어떤 문제가 해결되지만, 어떤 비용이나 복잡도가 새로 생기는가?
- 장애가 발생했을 때 이 개념은 문제를 예방하는가, 감지하는가, 복구하는가?
- PayFlow 코드에서 이 개념을 확인하려면 어떤 서비스, 테이블, 로그, 테스트를 봐야 하는가?

### 답안 예시

<details>
<summary>답안 예시 펼치기</summary>

#### 핵심 답변

Query Execution Plan 개념은 PayFlow에서 다음 이유로 중요하다.

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
Query Execution Plan 개념은 PayFlow에서 느린 송금/정산 쿼리의 병목을 추측이 아니라 실행 계획으로 찾기 위해 필요하다.
이 개념이 없으면 정산 대상 조회가 전체 테이블 스캔을 하며 배치 시간이 급격히 늘어날 수 있다.
그래서 코드에서는 EXPLAIN으로 인덱스, rows, filesort 여부를 확인하고 쿼리와 인덱스를 조정하며,
운영에서는 slow query log, 실행 계획 변화, 배치 처리 시간을 확인해야 한다.
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
Query Execution Plan 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.
