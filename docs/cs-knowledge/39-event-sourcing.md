# Event Sourcing

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

## 핵심 개념

### Event Sourcing의 핵심 아이디어

전통적인 데이터 저장 방식은 현재 상태를 저장하고 이전 상태를 덮어쓴다.

```text
// 전통적 방식
wallets 테이블:
  id: wallet-A
  balance: 75,000  // 현재 잔액만 저장, 이전 값은 사라짐
```

Event Sourcing은 상태를 만든 모든 이벤트를 순서대로 저장하고, 필요할 때 이벤트를 재생(replay)해 현재 상태를 계산한다.

```text
// Event Sourcing 방식
wallet_events 테이블:
  sequence: 1, type: WalletCreated,  amount: 0
  sequence: 2, type: MoneyDeposited, amount: +100,000
  sequence: 3, type: MoneyWithdrawn, amount: -10,000
  sequence: 4, type: MoneyDeposited, amount: +50,000
  sequence: 5, type: MoneyWithdrawn, amount: -65,000
  ...
현재 잔액 = 모든 이벤트 합산 = 75,000
```

이렇게 하면 "현재 잔액이 75,000원"이라는 사실뿐 아니라, "왜 75,000원이 됐는가"를 완전히 설명할 수 있다.

### 이벤트 저장소 (Event Store)

Event Store는 이벤트를 append-only(추가만 가능)로 저장하는 특별한 DB다. 한번 저장된 이벤트는 수정하거나 삭제하지 않는다. 이것이 Event Sourcing의 불변성(immutability) 원칙이다.

```text
이벤트 저장소의 특성:
- Append-only: 새 이벤트만 추가 가능, 기존 이벤트 수정/삭제 불가
- Ordered: 이벤트가 발생 순서대로 저장됨
- Complete: 모든 상태 변화가 기록됨
```

```sql
-- 이벤트 저장소 테이블 예시
CREATE TABLE wallet_events (
    id          UUID PRIMARY KEY,
    wallet_id   VARCHAR NOT NULL,
    sequence_no BIGINT NOT NULL,          -- 지갑별 이벤트 순번
    event_type  VARCHAR NOT NULL,
    payload     JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    UNIQUE(wallet_id, sequence_no)        -- 같은 지갑의 순번 중복 방지
);
```

### 상태 재생 (State Reconstruction)

현재 상태가 필요할 때는 이벤트 저장소에서 해당 Aggregate의 모든 이벤트를 읽어 순서대로 적용한다.

```java
// 이벤트 재생으로 지갑 상태 복원
public Wallet reconstruct(String walletId) {
    List<WalletEvent> events = eventStore.load(walletId);
    Wallet wallet = Wallet.empty();  // 초기 상태
    for (WalletEvent event : events) {
        wallet.apply(event);  // 각 이벤트 순서대로 적용
    }
    return wallet;  // 현재 상태
}

// Wallet 도메인 객체에서 이벤트 적용
class Wallet {
    private long balance;

    void apply(WalletEvent event) {
        switch (event.type()) {
            case DEPOSITED  -> this.balance += event.amount();
            case WITHDRAWN  -> this.balance -= event.amount();
            case REFUNDED   -> this.balance += event.amount();
        }
    }
}
```

### 스냅샷 (Snapshot)

이벤트가 매우 많이 쌓이면 재생 시간이 길어진다. 지갑에 수만 건의 이벤트가 있다면 매번 재생하는 것이 비효율적이다. 스냅샷은 특정 시점의 상태를 저장해 이후 재생 횟수를 줄인다.

```text
// 스냅샷 + 이벤트 재생
이벤트 1 ~ 1000 → 스냅샷 저장 (balance: 50,000)
이벤트 1001 ~ 1010 → 추가 발생

현재 상태 계산:
스냅샷 (balance: 50,000) + 이벤트 1001~1010 재생
// 전체 1010개 이벤트 재생 대신 스냅샷 + 10개만 재생
```

```sql
-- 스냅샷 테이블
CREATE TABLE wallet_snapshots (
    wallet_id      VARCHAR NOT NULL,
    sequence_no    BIGINT NOT NULL,   -- 어느 이벤트까지 반영된 스냅샷인지
    state          JSONB NOT NULL,    -- 그 시점의 상태
    created_at     TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (wallet_id, sequence_no)
);
```

### Event Sourcing이 주는 가치

**완전한 감사 추적 (Audit Trail)**

모든 상태 변화가 이벤트로 기록되므로, "언제 누가 얼마를 변경했는가"를 완벽하게 추적할 수 있다. 금융 시스템에서 규제 준수, 분쟁 해결에 중요하다.

**시점 재현 (Point-in-Time Recovery)**

특정 시점의 상태를 정확히 재현할 수 있다. "3개월 전 이 지갑의 잔액이 얼마였는가"를 이벤트 1~N번까지만 재생해 답할 수 있다.

**이벤트 재처리**

비즈니스 로직이 변경되었을 때 기존 이벤트를 새 로직으로 재처리해 읽기 모델을 재구성할 수 있다. 버그 수정 후 과거 데이터를 재처리하는 것도 가능하다.

**디버깅 용이성**

버그가 발생했을 때 이벤트 로그를 보면 어떤 사건이 어떤 순서로 발생했는지 정확히 알 수 있다.

### Event Sourcing의 단점과 복잡도

**조회 성능**

현재 상태를 얻으려면 이벤트를 재생해야 한다. 이벤트가 많아질수록 느려진다. 읽기 성능을 위해 별도의 조회 모델(CQRS의 Query Side)이 필요하다.

**이벤트 스키마 진화**

한번 저장된 이벤트는 변경할 수 없다. 비즈니스 요구사항이 바뀌어 이벤트 구조를 변경해야 한다면, 기존 이벤트와 새 이벤트를 모두 처리할 수 있는 마이그레이션 전략이 필요하다.

**학습 곡선**

Event Sourcing은 전통적인 CRUD와 사고방식이 다르다. 팀 전체가 이 패턴을 이해하고 있어야 한다. 단순한 CRUD에 Event Sourcing을 적용하면 과도한 복잡도가 생긴다.

### 원장(Ledger)과 Event Sourcing의 유사성

금융 시스템의 원장은 Event Sourcing과 본질적으로 같은 개념이다. 원장은 현재 잔액 대신 모든 거래 내역을 기록하고, 잔액은 항상 거래 합산으로 계산한다.

```text
원장 기록 (Event Sourcing과 동일한 개념):
  2026-05-01: 충전 +100,000원
  2026-05-10: 출금 -30,000원
  2026-05-20: 입금 +15,000원

현재 잔액 = 100,000 - 30,000 + 15,000 = 85,000원
```

이것이 PayFlow에서 원장 서비스가 중요한 이유다. 원장은 단순한 기록이 아니라 시스템의 "진실의 원천"이며, 잔액과 정산의 정합성을 검증하는 기준이 된다.

## PayFlow 연결

PayFlow의 원장 개념은 Event Sourcing과 비슷한 면이 있다. 잔액 자체보다 잔액을 변화시킨 근거 기록을 남기는 것이 중요하기 때문이다.

다만 모든 도메인을 Event Sourcing으로 구현하면 복잡도가 커진다. PayFlow에서는 원장과 이력 관리에 이 관점을 참고하면 좋다.

## 실무 포인트

- 이벤트는 변경 불가능하게 저장한다.
- 이벤트 스키마 변경 전략이 필요하다.
- 현재 상태 조회를 위해 스냅샷이나 조회 모델이 필요할 수 있다.
- 재생 순서와 중복 처리가 중요하다.
- 단순 CRUD에는 과할 수 있다.

## 체크 질문

- Event Sourcing과 일반 상태 저장의 차이는 무엇인가
- 원장 기록이 Event Sourcing과 닮은 점은 무엇인가
- Event Sourcing의 단점은 무엇인가

## 실무 설계 참고

### 대표 장애 시나리오

현재 잔액 숫자는 있는데 어떤 거래로 만들어졌는지 설명하지 못한다.

### 잘못된 구현 예시

~~~text
상태만 저장하고 상태를 만든 사건은 버린다.
~~~

### 좋은 구현 예시

~~~text
잔액 변경 이벤트나 원장 엔트리를 append-only로 남기고 필요 시 현재 상태를 계산한다.
~~~

### 대안과 선택 이유

분산 트랜잭션이나 모든 후속 처리를 동기 호출로 묶는 방식도 있지만, 서비스 하나의 장애가 전체 송금 실패로 번지기 쉽다. PayFlow는 로컬 트랜잭션, Saga, 도메인 이벤트, Outbox, 멱등성을 조합해 부분 실패를 상태로 남기고 복구하는 방식을 선택하는 것이 적합하다.

### PayFlow에서 확인할 위치

wallet history, ledger entries, event log

### 면접에서 설명하기

Event Sourcing의 핵심은 상태보다 사건을 신뢰하는 것이다. 다만 조회 성능과 스키마 진화 비용을 감수해야 한다.

### 관련 문서

36, 38, 46

## 질문의 질문으로 더 깊게 보기

### 이 문서에서 잡아야 할 CS 감각

이 구간의 핵심은 여러 서비스의 작업을 하나의 로컬 트랜잭션처럼 다룰 수 없다는 사실이다. 그래서 Saga, Domain Event, Outbox, 멱등성이 등장한다. 이들은 모두 "부분 실패를 어떻게 추적하고 복구할 것인가"에 대한 답이다.

### 질문의 질문

아래 질문들은 답을 외우기 위한 질문이 아니다. 하나씩 답하면서 "왜 그런 설계가 필요한지"를 설명하는 연습을 하기 위한 질문이다.

- Event Sourcing 개념이 없다면 PayFlow에서 가장 먼저 어떤 장애가 생기는가?
- 이 개념은 정확성, 성능, 보안, 운영성 중 무엇을 가장 크게 개선하는가?
- 반대로 이 개념을 잘못 적용하면 어떤 복잡도가 추가되는가?
- 이 개념을 적용하면 어떤 문제가 해결되지만, 어떤 비용이나 복잡도가 새로 생기는가?
- 장애가 발생했을 때 이 개념은 문제를 예방하는가, 감지하는가, 복구하는가?
- PayFlow 코드에서 이 개념을 확인하려면 어떤 서비스, 테이블, 로그, 테스트를 봐야 하는가?

### 답안 예시

<details>
<summary>답안 예시 펼치기</summary>

#### 핵심 답변

Event Sourcing 개념은 PayFlow에서 다음 이유로 중요하다.

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
Event Sourcing 개념은 PayFlow에서 현재 잔액만이 아니라 잔액을 만든 사건들의 기록으로 상태를 설명하기 위해 필요하다.
이 개념이 없으면 현재 잔액 숫자만 남아 있으면 어떤 충전/송금/보정 때문에 잔액이 바뀌었는지 추적할 수 없다.
그래서 코드에서는 잔액 변경 이력과 원장 이벤트를 append-only로 남기고 필요 시 스냅샷을 사용하며,
운영에서는 이력 누락, 재생 결과와 현재 잔액 차이, 보정 거래를 확인해야 한다.
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
Event Sourcing 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.

