# MySQL InnoDB Architecture

## 핵심 개념

### InnoDB 전체 구조

InnoDB는 MySQL의 대표적인 스토리지 엔진이다. 트랜잭션, Row-Level Lock, MVCC, Crash Recovery, Foreign Key를 지원한다. 데이터는 테이블스페이스(`.ibd` 파일)에 저장되고, 인덱스 구조는 B-Tree를 기반으로 한다.

```text
클라이언트 쿼리
  -> MySQL Server Layer (Parser, Optimizer, Executor)
  -> InnoDB Storage Engine
       -> Buffer Pool (메모리)
       -> Redo Log (WAL)
       -> Undo Log
       -> 데이터 파일 (.ibd)
```

### Buffer Pool: 성능의 핵심

Buffer Pool은 InnoDB가 디스크에서 읽어온 데이터 페이지(기본 16KB)와 인덱스 페이지를 메모리에 캐시하는 공간이다. 전체 DB 성능에서 가장 큰 영향을 미치는 설정 중 하나다.

- 읽기 요청: 먼저 Buffer Pool에서 찾고 없으면 디스크에서 읽어 Buffer Pool에 적재한다.
- 쓰기 요청: 먼저 Buffer Pool에 있는 페이지를 변경(Dirty Page)하고, 이후 백그라운드에서 디스크에 기록한다.

```text
innodb_buffer_pool_size = 메모리의 70~80% 권장
```

Buffer Pool이 작으면 캐시 미스가 자주 발생해 디스크 I/O가 증가한다. 잔액 조회가 잦은 wallet 테이블이 Buffer Pool에 올라와 있는지가 PayFlow 성능에 직접 영향을 준다.

### Redo Log: 장애 복구 보장

Redo Log는 Write-Ahead Logging(WAL) 방식으로 동작한다. 데이터 페이지를 디스크에 기록하기 전에 변경 내용을 먼저 Redo Log에 기록한다. 장애가 발생하면 재시작 시 Redo Log를 재실행(redo)해 커밋된 데이터를 복구한다.

```text
커밋 요청
  -> Redo Log에 변경 기록 (빠른 순차 쓰기)
  -> 트랜잭션 커밋 완료 (클라이언트에 응답)
  -> 백그라운드: Dirty Page를 디스크에 flush
```

`innodb_flush_log_at_trx_commit` 설정으로 내구성(Durability)과 성능 간의 trade-off를 조정할 수 있다.

- `1` (기본값): 커밋마다 Redo Log를 디스크에 기록. 가장 안전하지만 가장 느리다.
- `2`: 커밋마다 OS 버퍼에 기록. OS 장애 시 1초치 데이터 손실 가능.
- `0`: 1초마다 기록. DB 장애 시 1초치 데이터 손실 가능.

결제 시스템에서는 기본값 `1`이 권장된다.

### Undo Log와 MVCC

Undo Log는 트랜잭션이 변경하기 전의 이전 버전 데이터를 보관한다. 두 가지 목적으로 사용된다.

**롤백**: 트랜잭션이 롤백될 때 Undo Log를 이용해 이전 상태로 되돌린다.

**MVCC(Multi-Version Concurrency Control)**: 읽기 트랜잭션이 쓰기 트랜잭션을 기다리지 않고 일관된 스냅샷을 볼 수 있게 한다.

```text
T1 시작 (Read View 생성)
T2: balance 10000 -> 9000 변경, 커밋
T1: balance 조회 -> Undo Log에서 10000 반환 (T1 시작 시점 스냅샷)
T1 종료
```

장기 실행 트랜잭션은 Undo Log를 오래 유지해야 해 스토리지 압박을 일으킨다.

### Row-Level Lock과 인덱스의 관계

InnoDB는 행 단위 락을 지원하지만, 락은 행 자체가 아닌 인덱스 레코드에 걸린다. 인덱스가 없으면 조건에 맞는 행을 찾기 위해 전체 테이블을 스캔해야 하고, 이 과정에서 훨씬 넓은 범위에 락이 잡힌다.

```sql
-- wallet_id에 인덱스가 있는 경우: 해당 행만 락
SELECT * FROM wallet WHERE wallet_id = 1 FOR UPDATE;

-- status에 인덱스가 없는 경우: 조건에 맞지 않아도 스캔한 행에 락 걸릴 수 있음
SELECT * FROM wallet WHERE status = 'ACTIVE' FOR UPDATE;
```

### Gap Lock과 Next-Key Lock

InnoDB는 Phantom Read를 방지하기 위해 Gap Lock을 사용한다.

- **Record Lock**: 인덱스 레코드 자체에 대한 락
- **Gap Lock**: 인덱스 레코드 사이의 갭에 대한 락 (새 삽입 방지)
- **Next-Key Lock**: Record Lock + Gap Lock. 범위 조건 조회 시 기본으로 적용된다.

```sql
-- id가 5~10 사이 행을 조회하면 그 범위에 갭 락이 걸려
-- 다른 트랜잭션은 id 5~10에 새 행을 삽입할 수 없다
SELECT * FROM transfer WHERE id BETWEEN 5 AND 10 FOR UPDATE;
```

Gap Lock은 데드락의 원인이 될 수 있다. 락 범위를 좁히려면 정확한 인덱스를 사용하는 것이 중요하다.

### Clustered Index와 Secondary Index

InnoDB는 Primary Key를 기준으로 Clustered Index를 구성한다. 실제 데이터 행이 Primary Key 순서로 정렬되어 저장된다.

Secondary Index는 인덱스 컬럼 값과 함께 Primary Key 값을 저장한다. Secondary Index로 조회하면 우선 Secondary Index에서 Primary Key를 찾고, 다시 Clustered Index에서 실제 행을 조회하는 **두 번의 B-Tree 탐색**이 필요하다. 이를 **이중 탐색(Double Lookup)** 또는 인덱스 back-access라고 한다.

Covering Index를 사용하면 Secondary Index만으로 쿼리를 완료해 이중 탐색을 피할 수 있다.

### 흔한 오해와 함정

**"Row-Level Lock이니까 한 행만 잠긴다"**: 인덱스가 없거나 인덱스를 활용하지 못하면 더 넓은 범위가 잠긴다. `EXPLAIN`으로 인덱스 사용 여부를 확인해야 한다.

**"트랜잭션이 짧으면 Undo Log가 문제없다"**: 짧은 트랜잭션이 많아도 Undo Log는 모든 Read View가 살아있는 동안 유지된다. 오래된 트랜잭션(장기 실행 배치 등)이 있으면 Undo Log가 계속 쌓인다.

**"Buffer Pool이 크면 무조건 좋다"**: OS와 다른 프로세스가 사용할 메모리를 남겨야 한다. 메모리를 너무 많이 할당하면 OS가 스왑을 사용해 오히려 성능이 떨어진다.


## PayFlow 연결

PayFlow의 지갑 잔액, 송금 상태, 원장 기록은 MySQL InnoDB에 저장된다. 잔액 변경과 송금 상태 변경은 트랜잭션과 락, 인덱스 성능에 직접 영향을 받는다.

동시 송금 요청이 많으면 InnoDB의 row lock, deadlock, 인덱스 설계가 중요해진다.

## 실무 포인트

- InnoDB는 행 단위 락을 지원하지만 인덱스가 없으면 더 넓게 잠길 수 있다.
- MVCC 덕분에 읽기와 쓰기가 어느 정도 분리된다.
- 트랜잭션이 길면 Undo Log와 락 유지 시간이 늘어난다.
- Buffer Pool 크기는 DB 성능에 큰 영향을 준다.

## 체크 질문

- InnoDB에서 Buffer Pool은 어떤 역할을 하는가
- 인덱스가 없으면 락 범위가 넓어질 수 있는 이유는 무엇인가
- MVCC는 어떤 문제를 해결하는가

## 실무 설계 참고

### 대표 장애 시나리오

인덱스 없는 조건으로 잔액 행을 조회해 의도보다 넓은 락이 잡힌다.

### 잘못된 구현 예시

~~~text
InnoDB가 row lock을 지원하니 항상 한 행만 잠긴다고 생각한다.
~~~

### 좋은 구현 예시

~~~text
인덱스 설계, 트랜잭션 단축, 실행 계획 확인으로 락 범위를 줄인다.
~~~

### 대안과 선택 이유

애플리케이션 코드에서만 검증하는 방식도 있지만, 동시 요청과 장애 상황에서는 코드 검증만으로 부족하다. PayFlow는 DB 트랜잭션, 락, 유니크 제약, 인덱스, 실행 계획을 함께 사용해 데이터 계층에서도 불변식을 지키는 쪽이 더 안전하다.

### PayFlow에서 확인할 위치

MySQL schema, wallet table index, slow query/deadlock log

### 면접에서 설명하기

InnoDB의 MVCC와 lock은 결제 동시성의 바닥 지식이다. 인덱스가 락 범위에도 영향을 준다.

### 관련 문서

18, 23, 25, 27

## 질문의 질문으로 더 깊게 보기

### 이 문서에서 잡아야 할 CS 감각

이 구간의 핵심은 데이터가 동시에 바뀔 때 어떤 불변식을 지켜야 하는가다. 결제 시스템에서는 "잔액은 음수가 되면 안 된다", "같은 요청은 한 번만 반영되어야 한다" 같은 규칙이 코드보다 더 중요하다.

### 질문의 질문

아래 질문들은 답을 외우기 위한 질문이 아니다. 하나씩 답하면서 "왜 그런 설계가 필요한지"를 설명하는 연습을 하기 위한 질문이다.

- MySQL InnoDB Architecture 개념이 없다면 PayFlow에서 가장 먼저 어떤 장애가 생기는가?
- 이 개념은 정확성, 성능, 보안, 운영성 중 무엇을 가장 크게 개선하는가?
- 반대로 이 개념을 잘못 적용하면 어떤 복잡도가 추가되는가?
- 이 개념을 적용하면 어떤 문제가 해결되지만, 어떤 비용이나 복잡도가 새로 생기는가?
- 장애가 발생했을 때 이 개념은 문제를 예방하는가, 감지하는가, 복구하는가?
- PayFlow 코드에서 이 개념을 확인하려면 어떤 서비스, 테이블, 로그, 테스트를 봐야 하는가?

### 답안 예시

<details>
<summary>답안 예시 펼치기</summary>

#### 핵심 답변

MySQL InnoDB Architecture 개념은 PayFlow에서 다음 이유로 중요하다.

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
MySQL InnoDB Architecture 개념은 PayFlow에서 InnoDB의 트랜잭션, MVCC, 락 동작을 이해하고 잔액 변경을 안전하게 처리하기 위해 필요하다.
이 개념이 없으면 인덱스 없는 조건으로 잔액 행을 찾다가 넓은 락이 잡혀 송금 요청들이 대기할 수 있다.
그래서 코드에서는 적절한 인덱스, 짧은 트랜잭션, row lock 전략을 사용하고,
운영에서는 lock wait, deadlock, buffer pool hit ratio, redo/undo 증가를 확인해야 한다.
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
MySQL InnoDB Architecture 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.
