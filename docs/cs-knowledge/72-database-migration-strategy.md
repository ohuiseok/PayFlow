# Database Migration Strategy

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

## 핵심 개념

DB Migration은 스키마 변경을 안전하게 적용하는 전략이다. 운영 DB는 애플리케이션 배포와 함께 변경되므로 하위 호환성과 롤백 가능성을 고려해야 한다.

### 왜 DB 마이그레이션이 어려운가

단일 서버 시스템이라면 애플리케이션을 내리고, DB를 변경하고, 다시 올리면 된다. 하지만 운영 환경에서는 세 가지 제약이 존재한다.

첫째, 다운타임이 없어야 한다. 결제 시스템은 24시간 운영되므로 스키마 변경 중에도 트래픽을 처리해야 한다.

둘째, 배포 순서 문제가 있다. 서버 10대를 순차적으로 배포할 때, 구버전 서버와 신버전 서버가 동시에 같은 DB를 사용한다. 신버전만 이해하는 컬럼을 추가하면 구버전 서버가 오류를 낼 수 있다.

셋째, 롤백이 필요할 수 있다. 배포 후 문제가 발견되면 코드를 롤백해야 하는데, 이미 변경된 DB 스키마를 롤백하기 어려울 수 있다.

### Expand-Contract 패턴 (병렬 변경 패턴)

운영 DB에서 컬럼을 안전하게 변경하는 표준 전략이다. 3단계로 나눠 진행한다.

**1단계 Expand (확장)**: 새로운 컬럼이나 테이블을 추가한다. 기존 코드는 영향받지 않는다.

```sql
-- 기존: user_name VARCHAR(50)
-- 변경 목표: name을 first_name, last_name으로 분리
ALTER TABLE users ADD COLUMN first_name VARCHAR(50);
ALTER TABLE users ADD COLUMN last_name VARCHAR(50);
```

**2단계 Migrate (이전)**: 새 코드를 배포해 기존 컬럼과 새 컬럼 모두를 쓰도록 한다. 기존 데이터를 새 형식으로 복사한다.

```sql
-- 데이터 이전
UPDATE users SET first_name = SPLIT_PART(user_name, ' ', 1),
                last_name = SPLIT_PART(user_name, ' ', 2);
```

**3단계 Contract (수축)**: 기존 컬럼을 제거한다. 이때는 이미 모든 코드가 새 컬럼만 사용하는 상태다.

```sql
ALTER TABLE users DROP COLUMN user_name;
```

각 단계는 독립적인 배포로 처리한다. 이렇게 하면 어느 단계에서든 롤백해도 안전하다.

### Flyway와 Liquibase

마이그레이션 도구는 스키마 변경 이력을 관리하고 자동으로 적용한다.

**Flyway**는 SQL 파일 또는 Java 코드로 마이그레이션을 작성한다. 파일명 규칙(V1__init.sql, V2__add_column.sql)으로 순서를 정한다.

```
resources/
  db/migration/
    V1__create_transfer_table.sql
    V2__add_idempotency_key.sql
    V3__add_index_on_created_at.sql
```

```sql
-- V2__add_idempotency_key.sql
ALTER TABLE transfers ADD COLUMN idempotency_key VARCHAR(64);
CREATE UNIQUE INDEX idx_transfers_idempotency_key ON transfers(idempotency_key);
```

Flyway는 `flyway_schema_history` 테이블에 적용된 마이그레이션 이력을 저장한다. 이미 적용된 스크립트는 다시 실행하지 않는다.

```yaml
# Spring Boot application.yml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    out-of-order: false  # 순서 강제
```

**Liquibase**는 XML, YAML, JSON, SQL로 변경 내역(changelog)을 관리한다. 롤백 명령이 더 명확하다는 장점이 있다.

### 대용량 테이블 마이그레이션의 위험

수억 건의 데이터가 있는 테이블에서 `ALTER TABLE`을 실행하면 테이블 전체에 락이 걸려 수십 분~수 시간 동안 서비스가 중단될 수 있다.

안전한 대안은 다음과 같다.

**pt-online-schema-change (Percona Toolkit)**: 새 테이블을 만들고, 트리거로 변경사항을 동기화하면서 데이터를 복사한다. 완료되면 테이블을 교체한다. 운영 중 변경 가능하지만 트리거 오버헤드가 있다.

**gh-ost (GitHub)**: 트리거 대신 MySQL 바이너리 로그를 파싱해 변경사항을 동기화한다. pt-osc보다 운영 영향이 적다.

**배치 업데이트**: 인덱스 추가나 컬럼 기본값 설정 등은 배치로 나눠 처리한다.

```sql
-- 1000건씩 나눠 업데이트
UPDATE transfers SET status = 'COMPLETED'
WHERE id BETWEEN 1 AND 1000
  AND status IS NULL;
-- 반복...
```

### 롤백 전략

마이그레이션이 실패했을 때의 복구 전략이다.

**Flyway Repair**: 마이그레이션이 실패하면 `flyway_schema_history`에 실패 기록이 남는다. `flyway repair`로 실패 기록을 지우고 다시 시도할 수 있다.

**스냅샷**: 대규모 마이그레이션 전에 DB 스냅샷을 찍어두면 최악의 경우 복원할 수 있다. 단, 스냅샷 이후의 데이터는 손실된다.

**전진 전용 전략**: 일부 팀은 마이그레이션을 롤백하지 않고 새 마이그레이션 스크립트로 되돌리는 방식을 택한다. 단순성을 높이지만 빠른 롤백이 어렵다.

### 흔한 오해와 함정

**함정 1: 컬럼 이름 변경은 단순하다**

`ALTER TABLE transfers RENAME COLUMN amount TO transfer_amount`는 스키마 변경은 쉽지만, 기존 코드가 `amount`를 참조하면 즉시 오류가 발생한다. Expand-Contract 패턴으로 새 컬럼 추가 → 데이터 복사 → 기존 컬럼 제거 순으로 처리해야 한다.

**함정 2: NOT NULL 컬럼 추가는 빠르다**

`ALTER TABLE ... ADD COLUMN new_col VARCHAR(10) NOT NULL`은 기존 행에 기본값이 없으면 실패하고, 있어도 전체 테이블을 재작성해야 할 수 있다. `NOT NULL DEFAULT 'value'`로 추가 후 나중에 DEFAULT를 제거하는 것이 안전하다.

**함정 3: 인덱스 추가는 서비스 중 가능하다**

MySQL의 `ADD INDEX`는 대부분 Online DDL을 지원하지만, 데이터가 많으면 상당한 I/O가 발생해 서비스 성능이 저하된다. 트래픽이 낮은 시간대에 실행하거나 pt-osc를 사용해야 한다.

### Trade-off

Expand-Contract는 안전하지만 배포가 3단계로 늘어나 개발 속도가 느려진다. 스타트업 초기처럼 다운타임이 허용되는 환경에서는 단순히 DB를 변경하고 배포하는 것이 더 실용적일 수 있다. 운영 성숙도와 비즈니스 요구에 따라 전략을 선택해야 한다.

## PayFlow 연결

PayFlow에서 송금 테이블에 새 상태 컬럼을 추가하거나, Outbox 테이블에 인덱스를 추가하거나, 정산 결과 테이블을 변경할 수 있다. 잘못된 마이그레이션은 결제 API 장애로 이어질 수 있다.

## 실무 포인트

- 컬럼 추가는 nullable 또는 기본값 전략을 고려한다.
- 컬럼 삭제는 단계적으로 진행한다.
- 대용량 테이블 인덱스 추가는 락과 시간을 고려한다.
- 애플리케이션 배포와 DB 변경 순서를 맞춘다.
- 마이그레이션 도구를 사용해 이력을 관리한다.

## 체크 질문

- 운영 DB에서 컬럼을 바로 삭제하면 왜 위험한가
- 애플리케이션 배포와 DB 마이그레이션 순서가 중요한 이유는 무엇인가
- 대용량 테이블에 인덱스를 추가할 때 주의할 점은 무엇인가

## 실무 설계 참고

### 대표 장애 시나리오

컬럼을 바로 삭제해 배포 중인 구버전 서비스가 SQL 오류를 낸다.

### 잘못된 구현 예시

~~~text
DB migration과 application deploy를 한 번에 바꾸면 된다고 생각한다.
~~~

### 좋은 구현 예시

~~~text
컬럼 추가, 코드 전환, 구컬럼 제거를 단계적으로 나누고 하위 호환을 유지한다.
~~~

### 대안과 선택 이유

장애가 나면 사람이 재시작하거나 배포를 되돌리는 방식도 가능하지만, 결제 시스템에서는 장애 전파 속도가 빠르고 수동 대응은 늦다. PayFlow는 Timeout, Retry 제한, Circuit Breaker, Bulkhead, Graceful Shutdown, 호환 배포를 통해 피해 범위를 자동으로 줄이는 편이 낫다.

### PayFlow에서 확인할 위치

migration scripts, entity fields, repository queries

### 면접에서 설명하기

DB 마이그레이션은 코드 배포보다 되돌리기 어렵다. 그래서 단계적 호환성이 중요하다.

### 관련 문서

06, 73, 74

## 질문의 질문으로 더 깊게 보기

### 이 문서에서 잡아야 할 CS 감각

이 구간의 핵심은 장애를 예외 상황이 아니라 설계 입력으로 보는 것이다. Timeout, Retry, Circuit Breaker, Graceful Shutdown, 배포 전략은 모두 장애가 났을 때 피해를 제한하고 복구 가능하게 만드는 장치다.

### 질문의 질문

아래 질문들은 답을 외우기 위한 질문이 아니다. 하나씩 답하면서 "왜 그런 설계가 필요한지"를 설명하는 연습을 하기 위한 질문이다.

- 배포 중 구버전과 신버전이 동시에 존재해도 계약이 깨지지 않는가?
- 종료 또는 재시작 중 처리하던 요청은 어디까지 완료되었다고 볼 수 있는가?
- 롤백했을 때 DB 스키마와 이벤트 스키마도 함께 되돌릴 수 있는가?
- 이 개념을 적용하면 어떤 문제가 해결되지만, 어떤 비용이나 복잡도가 새로 생기는가?
- 장애가 발생했을 때 이 개념은 문제를 예방하는가, 감지하는가, 복구하는가?
- PayFlow 코드에서 이 개념을 확인하려면 어떤 서비스, 테이블, 로그, 테스트를 봐야 하는가?

### 답안 예시

<details>
<summary>답안 예시 펼치기</summary>

#### 핵심 답변

Database Migration Strategy 개념은 PayFlow에서 다음 이유로 중요하다.

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
Database Migration Strategy 개념은 PayFlow에서 운영 DB 스키마 변경이 배포 중인 여러 서비스 버전을 깨지 않게 하기 위해 필요하다.
이 개념이 없으면 컬럼을 바로 삭제해 구버전 transfer-service가 배포 중 SQL 오류를 내며 송금 처리가 실패할 수 있다.
그래서 코드에서는 expand-migrate-contract 방식, 하위 호환 컬럼 추가, 단계적 삭제를 적용하고,
운영에서는 migration 실패, SQL 오류, 구버전 API 호출 비율을 확인해야 한다.
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
Database Migration Strategy 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.

