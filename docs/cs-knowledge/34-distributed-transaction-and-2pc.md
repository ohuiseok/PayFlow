# Distributed Transaction And 2PC

## 핵심 개념

### 분산 트랜잭션 문제의 본질

단일 DB에서는 하나의 트랜잭션으로 여러 테이블의 변경을 원자적으로 처리할 수 있다. COMMIT이 되면 모두 반영되고, ROLLBACK이 되면 아무것도 반영되지 않는다.

MSA에서는 각 서비스가 자신의 DB를 소유한다. `transfer-service`의 송금 기록 생성과 `wallet-service`의 잔액 차감을 하나의 트랜잭션으로 묶을 수 없다. 이것이 분산 트랜잭션 문제다.

```text
// 이상적이지만 MSA에서는 불가능한 시나리오
BEGIN TRANSACTION;
  transfer_db.INSERT INTO transfers (id, amount, status) VALUES (...);
  wallet_db.UPDATE wallets SET balance = balance - 10000 WHERE id = ...;
COMMIT;  -- 두 DB에 동시에 적용??
```

각 DB는 독립적인 프로세스이므로 이런 원자적 커밋이 불가능하다.

### 2PC(Two-Phase Commit) 동작 원리

2PC는 분산 트랜잭션을 조율하기 위한 프로토콜이다. Coordinator(조율자)와 여러 Participant(참여자)로 구성된다.

**Phase 1: Prepare (준비 단계)**

Coordinator가 모든 Participant에게 "커밋할 준비가 됐는가?"라고 묻는다. 각 Participant는 트랜잭션을 완료할 수 있으면 "Yes", 아니면 "No"를 응답하고 준비 상태를 로그에 기록한다.

```text
Coordinator → transfer-service: "커밋 준비됐나?"
Coordinator → wallet-service:   "커밋 준비됐나?"

transfer-service → Coordinator: "Yes (준비 완료)"
wallet-service   → Coordinator: "Yes (준비 완료)"
```

**Phase 2: Commit 또는 Rollback**

모든 Participant가 Yes를 응답하면 Coordinator가 COMMIT 명령을 보낸다. 하나라도 No를 응답하면 ROLLBACK 명령을 보낸다.

```text
// 모두 Yes일 때
Coordinator → transfer-service: "COMMIT"
Coordinator → wallet-service:   "COMMIT"

// 하나라도 No일 때
Coordinator → transfer-service: "ROLLBACK"
Coordinator → wallet-service:   "ROLLBACK"
```

### 2PC의 문제점

**블로킹 프로토콜**

Phase 1에서 준비 완료를 응답한 Participant는 Coordinator의 Phase 2 명령을 기다리며 락을 유지한다. Coordinator가 이 순간 죽으면, Participant는 락을 풀어야 할지 유지해야 할지 알 수 없어 무한 대기 상태가 된다.

이것이 2PC가 "블로킹 프로토콜"이라 불리는 이유다. Coordinator 장애가 모든 Participant를 블로킹시킨다.

**Coordinator 단일 장애점**

Coordinator가 죽으면 전체 분산 트랜잭션이 멈춘다. Coordinator를 복구하려면 Phase 1에서 응답한 상태를 영구 로그에서 복구해야 하는데, 이 복구 과정 자체가 복잡하다.

**성능 문제**

2PC는 최소 2번의 왕복 통신(Round Trip)이 필요하다. 참여 서비스가 많을수록, 네트워크 지연이 클수록 전체 처리 시간이 길어진다. 그동안 모든 Participant의 DB 행에 락이 걸려 있다.

**네트워크 분할 시 불일치 가능성**

Coordinator가 COMMIT 명령을 보내던 중 네트워크가 분할되면, 일부 Participant는 커밋하고 나머지는 커밋하지 못하는 불일치가 발생할 수 있다. 이론적으로는 3PC(Three-Phase Commit)가 이를 개선하지만 실무에서는 거의 사용하지 않는다.

### XA 트랜잭션

Java EE, JTA(Java Transaction API)에서 2PC를 지원하는 표준이 XA 트랜잭션이다. 여러 데이터 소스(DB, 메시지 브로커 등)를 하나의 글로벌 트랜잭션으로 묶을 수 있다.

```java
// JTA XA 트랜잭션 예시 (레거시 Java EE 방식)
@Transactional
public void transfer() {
    // 두 개의 다른 DataSource가 하나의 XA 트랜잭션 안에 있음
    transferRepository.save(transfer);   // DataSource A
    walletRepository.debit(amount);      // DataSource B
}
```

하지만 이 방식은 다음 이유로 MSA에서는 현실적이지 않다.

- 모든 DB 드라이버가 XA를 지원하지 않는다.
- 성능 오버헤드가 크다.
- 분리된 서비스 프로세스 간에는 적용하기 어렵다.
- 클라우드 환경에서 Coordinator 장애 복구가 복잡하다.

### MSA에서 2PC를 피하는 이유와 대안

MSA에서 2PC를 실용적으로 사용하기 어렵기 때문에 다음 대안이 발전했다.

**Saga 패턴**: 여러 로컬 트랜잭션을 순서대로 실행하고, 실패 시 이전 단계를 보상 트랜잭션으로 되돌린다. 강한 일관성 대신 최종 정합성을 목표로 한다.

**Transactional Outbox**: DB 트랜잭션 안에 이벤트 발행 기록을 함께 저장하고, 별도 프로세스가 이를 읽어 메시지 브로커로 발행한다. DB 저장과 이벤트 발행 사이의 원자성 문제를 해결한다.

**멱등성 + 재시도**: 각 단계를 멱등하게 만들어 실패 시 안전하게 재시도할 수 있게 한다.

| 방식 | 일관성 | 가용성 | 구현 복잡도 | 적용 상황 |
|------|--------|--------|-----------|---------|
| 2PC | 강한 일관성 | 낮음 | 높음 | 소수의 DB, 레거시 엔터프라이즈 |
| Saga | 최종 정합성 | 높음 | 중간 | MSA, 분산 서비스 |
| Outbox | 최소 1회 전달 | 높음 | 중간 | DB + 이벤트 원자성 |

### CAP 정리와의 관계

CAP 정리에 따르면 분산 시스템에서 Consistency(일관성), Availability(가용성), Partition Tolerance(분할 내성) 세 가지를 동시에 완전히 만족할 수 없다.

2PC는 CA를 선택한다. 네트워크 분할이 없을 때는 강한 일관성을 제공하지만, 분할이 발생하면 가용성을 포기(블로킹)하거나 일관성을 포기해야 한다.

MSA에서 Saga는 AP를 선택한다. 네트워크 분할이 있어도 가용성을 유지하지만, 일관성은 최종적으로만 보장한다.

결제 시스템에서는 "잠깐 일관성이 어긋날 수 있지만 시스템은 계속 동작한다"는 방식이 "일관성을 위해 시스템 전체가 멈춘다"는 방식보다 실무에서 더 수용 가능하다. 단, 최종 정합성을 검증하는 원장과 대사 구조가 반드시 있어야 한다.

## PayFlow 연결

송금은 `transfer-service`, `wallet-service`, `ledger-service`가 관련되므로 분산 트랜잭션처럼 보일 수 있다. 하지만 MSA에서 2PC를 사용하면 성능, 장애 복구, 운영 복잡도가 커진다.

PayFlow는 2PC 대신 로컬 트랜잭션, Outbox, 이벤트, Saga, 보상 처리로 정합성을 맞추는 방향이 더 현실적이다.

## 실무 포인트

- 2PC는 참여자 중 하나가 느리면 전체가 느려진다.
- Coordinator 장애가 복구 문제를 만든다.
- MSA에서는 Saga와 최종 정합성이 자주 사용된다.
- 돈의 핵심 변경은 각 서비스의 로컬 트랜잭션 안에서 확실히 처리한다.

## 체크 질문

- 2PC는 어떤 단계로 동작하는가
- MSA에서 2PC를 피하는 이유는 무엇인가
- PayFlow에서 2PC 대신 어떤 패턴을 사용하는가

## 실무 설계 참고

### 대표 장애 시나리오

송금, 지갑, 원장을 모두 하나의 분산 트랜잭션으로 묶으려다 장애 복구가 복잡해진다.

### 잘못된 구현 예시

~~~text
2PC가 있으면 MSA 정합성 문제가 깔끔히 해결된다고 생각한다.
~~~

### 좋은 구현 예시

~~~text
로컬 트랜잭션과 Saga, Outbox, 보상 처리를 조합한다.
~~~

### 대안과 선택 이유

분산 트랜잭션이나 모든 후속 처리를 동기 호출로 묶는 방식도 있지만, 서비스 하나의 장애가 전체 송금 실패로 번지기 쉽다. PayFlow는 로컬 트랜잭션, Saga, 도메인 이벤트, Outbox, 멱등성을 조합해 부분 실패를 상태로 남기고 복구하는 방식을 선택하는 것이 적합하다.

### PayFlow에서 확인할 위치

transfer-service, wallet-service, ledger-service transaction boundaries

### 면접에서 설명하기

2PC는 강한 일관성을 주지만 지연과 coordinator 장애 비용이 크다. MSA에서는 보상 가능한 설계가 자주 선택된다.

### 관련 문서

35, 37, 59

## 질문의 질문으로 더 깊게 보기

### 이 문서에서 잡아야 할 CS 감각

이 구간의 핵심은 여러 서비스의 작업을 하나의 로컬 트랜잭션처럼 다룰 수 없다는 사실이다. 그래서 Saga, Domain Event, Outbox, 멱등성이 등장한다. 이들은 모두 "부분 실패를 어떻게 추적하고 복구할 것인가"에 대한 답이다.

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

Distributed Transaction And 2PC 개념은 PayFlow에서 다음 이유로 중요하다.

- Saga, 이벤트, 멱등성 개념은 PayFlow에서 여러 서비스에 걸친 송금 흐름을 부분 실패에도 복구 가능하게 만들기 위해 필요하다.
- transfer-service의 상태 머신과 outbox_event, wallet-service의 잔액 변경, ledger-service의 처리 이벤트가 기준이다.
- 출금 성공 후 입금 실패, 송금 완료 후 이벤트 발행 실패, 같은 요청 재시도가 대표적인 위험이다.
- 명확한 상태 전이, 보상 트랜잭션, Domain Event, Outbox, Idempotency-Key, Consumer 멱등성으로 방어한다.
- 운영에서는 COMPENSATION_REQUIRED 건수, Outbox 실패 건수, idempotency hit 수, 보상 실패 건수, 상태별 송금 수를 본다.

#### 질문에 답하는 방식

좋은 답변은 용어 정의에서 멈추지 않고 다음 순서로 이어져야 한다.

1. 어떤 데이터나 요청을 보호하려는 개념인지 말한다.
2. 그 데이터의 진실의 원천이 어느 서비스에 있는지 말한다.
3. 장애, 중복, 동시성, 지연 중 어떤 상황에서 문제가 생기는지 설명한다.
4. 코드 레벨의 방어 수단과 운영 레벨의 확인 수단을 함께 말한다.

#### PayFlow 예시 답변

```text
Distributed Transaction And 2PC 개념은 PayFlow에서 여러 서비스 DB를 하나의 거대한 트랜잭션으로 묶으려는 위험을 이해하기 위해 필요하다.
이 개념이 없으면 2PC coordinator 장애나 참여자 지연 때문에 송금 전체가 잠기고 복구가 어려워질 수 있다.
그래서 코드에서는 로컬 트랜잭션, Saga, Outbox, 보상 처리로 분산 정합성을 설계하고,
운영에서는 보상 필요 상태, Outbox 지연, 서비스별 커밋 상태를 확인해야 한다.
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
Distributed Transaction And 2PC 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.
