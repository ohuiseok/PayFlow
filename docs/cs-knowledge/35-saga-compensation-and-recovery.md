# Saga Compensation And Recovery

## 핵심 개념

### Saga 패턴이란

Saga는 여러 서비스에 걸친 긴 비즈니스 트랜잭션을 여러 개의 로컬 트랜잭션으로 분해하고, 각 단계의 성공과 실패를 관리하는 패턴이다. 중간 단계가 실패하면 이미 성공한 이전 단계들을 보상 트랜잭션(Compensating Transaction)으로 되돌린다.

2PC가 "모든 참여자가 동의할 때만 커밋"이라면, Saga는 "각자 커밋하되, 실패하면 역방향으로 되돌린다"는 접근이다.

### 두 가지 구현 방식

**Choreography (안무형)**

각 서비스가 자신의 로컬 트랜잭션을 완료한 후 이벤트를 발행하고, 다음 서비스가 그 이벤트를 소비해 자신의 단계를 실행한다. 중앙 조율자 없이 서비스들이 자율적으로 협력한다.

```text
transfer-service: 송금 생성 → TransferRequested 이벤트 발행
wallet-service:   TransferRequested 소비 → 잔액 차감 → MoneyDebited 이벤트 발행
ledger-service:   MoneyDebited 소비 → 원장 기록 → LedgerRecorded 이벤트 발행
transfer-service: LedgerRecorded 소비 → 송금 완료 처리
```

장점: 서비스 간 결합도 낮음, 새 서비스 추가 용이.
단점: 전체 흐름을 한 곳에서 파악하기 어려움, 순환 의존 위험.

**Orchestration (조율형)**

중앙 Orchestrator가 각 서비스에 명령을 보내고 응답을 받아 다음 단계를 결정한다.

```text
Orchestrator → transfer-service: "잔액 차감해"
transfer-service → Orchestrator: "차감 완료"
Orchestrator → ledger-service: "원장 기록해"
ledger-service → Orchestrator: "기록 완료"
Orchestrator: "전체 완료"
```

장점: 전체 흐름을 한 곳에서 파악 가능, 복잡한 조건 처리 쉬움.
단점: Orchestrator가 병목이자 단일 장애점이 될 수 있음.

### 보상 트랜잭션 (Compensating Transaction)

보상 트랜잭션은 단순한 DB ROLLBACK이 아니다. 이미 커밋되어 실행된 작업을 논리적으로 되돌리는 반대 작업이다.

```text
원래 작업: 지갑에서 10,000원 차감
보상 작업: 지갑에 10,000원 환급 (새로운 트랜잭션으로)
```

보상 트랜잭션이 원래 작업의 ROLLBACK과 다른 이유가 있다.

- 이미 커밋된 작업은 DB가 자동으로 되돌릴 수 없다.
- 외부 시스템(예: 은행 API)에 이미 반영된 작업은 취소 API를 호출해야 한다.
- 이미 전송된 이메일/문자는 취소할 수 없다. (보상 불가능한 작업)

**보상 가능한 작업**: 잔액 차감/증가, 상태 변경, 예약
**보상 불가능한 작업**: 이메일 발송, 외부 API 취소 불가 작업

### 상태 머신으로 Saga 흐름 관리

Saga의 각 단계는 명확한 상태로 표현되어야 한다. 중간에 프로세스가 죽어도 재시작 후 어느 단계에서 실패했는지 알고 재처리할 수 있어야 하기 때문이다.

```text
송금 상태 머신 예시:

PENDING
  → (잔액 차감 성공) → DEBITED
  → (잔액 차감 실패) → FAILED

DEBITED
  → (원장 기록 성공) → LEDGER_RECORDED
  → (원장 기록 실패) → COMPENSATION_REQUIRED

COMPENSATION_REQUIRED
  → (환급 성공) → COMPENSATED
  → (환급 실패) → COMPENSATION_FAILED  ← 운영자 수동 개입 필요

LEDGER_RECORDED
  → (정산 처리 성공) → COMPLETED
```

```java
// 상태 전이 예시
public class TransferSaga {
    public void onMoneyDebited(TransferId transferId) {
        Transfer transfer = transferRepository.findById(transferId);
        transfer.transitionTo(TransferStatus.DEBITED);
        // 다음 단계: 원장 기록 요청
        ledgerEventPublisher.publish(new RecordLedgerCommand(transferId));
    }

    public void onLedgerFailed(TransferId transferId) {
        Transfer transfer = transferRepository.findById(transferId);
        transfer.transitionTo(TransferStatus.COMPENSATION_REQUIRED);
        // 보상 트랜잭션 시작: 환급 요청
        walletEventPublisher.publish(new RefundCommand(transferId));
    }
}
```

### 보상 트랜잭션도 멱등해야 한다

보상 트랜잭션을 실행하는 중에도 장애가 발생할 수 있다. 환급 요청을 두 번 보내서 10,000원이 두 번 환급되면 안 된다. 보상 트랜잭션도 멱등하게 설계해야 한다.

```java
// 멱등한 환급 처리 예시
public void refund(TransferId transferId, long amount) {
    // 이미 환급된 경우 중복 처리 방지
    if (walletHistoryRepository.existsRefundFor(transferId)) {
        log.warn("이미 환급 처리된 송금입니다. transferId={}", transferId);
        return;
    }
    wallet.credit(amount);
    walletHistoryRepository.save(new WalletHistory(transferId, amount, REFUND));
}
```

### 보상 불가능한 상태와 운영자 개입

모든 실패를 자동으로 복구할 수 없다. 보상 트랜잭션 자체가 실패하거나, 외부 시스템이 이미 처리를 완료한 경우에는 자동 복구가 불가능하다. 이때는 `COMPENSATION_FAILED` 같은 상태로 두고, 운영자가 수동으로 개입해 처리해야 한다.

중요한 것은 이런 상태를 숨기거나 무시하면 안 된다는 점이다. 실패 상태는 명확하게 기록되어야 하고, 운영팀이 즉시 인지할 수 있도록 알림 메커니즘이 있어야 한다.

### 재처리 가능한 설계 원칙

Saga가 중간에 멈췄을 때 안전하게 재시작하려면 다음 원칙이 필요하다.

- **현재 상태 저장**: 어느 단계까지 완료됐는지 DB에 저장한다.
- **멱등한 단계**: 같은 단계를 두 번 실행해도 결과가 같다.
- **타임아웃과 재시도**: 응답이 없으면 일정 시간 후 재시도한다.
- **DLQ 처리**: 반복 실패 이벤트는 Dead Letter Queue로 보내 별도 처리한다.

### 흔한 오해

"Saga는 2PC보다 덜 안전하다"는 말은 반만 맞다. Saga는 강한 일관성 대신 최종 정합성을 제공한다. 하지만 잘 설계된 Saga는 모든 실패를 추적하고 복구할 수 있어서, 실무에서는 MSA 환경에서 2PC보다 더 신뢰성 높은 구조를 만들 수 있다.

"보상 트랜잭션은 완벽하다"는 것도 오해다. 보상 자체가 실패할 수 있고, 보상 불가능한 작업도 있다. Saga를 설계할 때는 보상이 실패했을 때의 처리까지 미리 생각해야 한다.

## PayFlow 연결

송금에서 출금은 성공했지만 입금이 실패하면 단순 롤백이 어렵다. 이미 `wallet-service`에서 출금 트랜잭션이 커밋되었을 수 있기 때문이다.

이때 `transfer-service`는 상태를 `COMPENSATION_REQUIRED`로 두고, 출금 취소 또는 환급 같은 보상 작업을 수행해야 한다.

## 실무 포인트

- 각 단계의 성공과 실패 상태를 명확히 저장한다.
- 보상 작업도 멱등해야 한다.
- 보상 실패 상태도 숨기지 않고 남긴다.
- 재처리 가능한 상태 머신이 필요하다.
- 사람이 개입해야 하는 장애 상태를 구분한다.

## 체크 질문

- Saga와 DB 롤백의 차이는 무엇인가
- 출금 성공 후 입금 실패 시 어떤 보상이 필요한가
- 보상 트랜잭션도 멱등해야 하는 이유는 무엇인가

## 실무 설계 참고

### 대표 장애 시나리오

출금은 성공했지만 입금이 실패해 송금 금액이 중간에 떠 있는 상태가 된다.

### 잘못된 구현 예시

~~~text
이미 커밋된 출금을 DB rollback처럼 되돌릴 수 있다고 생각한다.
~~~

### 좋은 구현 예시

~~~text
상태 머신과 보상 트랜잭션, 보상 실패 상태, 재처리 작업을 둔다.
~~~

### 대안과 선택 이유

분산 트랜잭션이나 모든 후속 처리를 동기 호출로 묶는 방식도 있지만, 서비스 하나의 장애가 전체 송금 실패로 번지기 쉽다. PayFlow는 로컬 트랜잭션, Saga, 도메인 이벤트, Outbox, 멱등성을 조합해 부분 실패를 상태로 남기고 복구하는 방식을 선택하는 것이 적합하다.

### PayFlow에서 확인할 위치

transfer status enum, wallet refund API, compensation job

### 면접에서 설명하기

Saga는 롤백이 아니라 이미 일어난 일을 반대 거래로 보정하는 흐름이다.

### 관련 문서

34, 36, 40, 45

## 질문의 질문으로 더 깊게 보기

### 이 문서에서 잡아야 할 CS 감각

이 구간의 핵심은 여러 서비스의 작업을 하나의 로컬 트랜잭션처럼 다룰 수 없다는 사실이다. 그래서 Saga, Domain Event, Outbox, 멱등성이 등장한다. 이들은 모두 "부분 실패를 어떻게 추적하고 복구할 것인가"에 대한 답이다.

### 질문의 질문

아래 질문들은 답을 외우기 위한 질문이 아니다. 하나씩 답하면서 "왜 그런 설계가 필요한지"를 설명하는 연습을 하기 위한 질문이다.

- Saga Compensation And Recovery 개념이 없다면 PayFlow에서 가장 먼저 어떤 장애가 생기는가?
- 이 개념은 정확성, 성능, 보안, 운영성 중 무엇을 가장 크게 개선하는가?
- 반대로 이 개념을 잘못 적용하면 어떤 복잡도가 추가되는가?
- 이 개념을 적용하면 어떤 문제가 해결되지만, 어떤 비용이나 복잡도가 새로 생기는가?
- 장애가 발생했을 때 이 개념은 문제를 예방하는가, 감지하는가, 복구하는가?
- PayFlow 코드에서 이 개념을 확인하려면 어떤 서비스, 테이블, 로그, 테스트를 봐야 하는가?

### 답안 예시

<details>
<summary>답안 예시 펼치기</summary>

#### 핵심 답변

Saga Compensation And Recovery 개념은 PayFlow에서 다음 이유로 중요하다.

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
Saga Compensation And Recovery 개념은 PayFlow에서 출금 성공 후 입금 실패 같은 부분 실패를 추적하고 보상하기 위해 필요하다.
이 개념이 없으면 보내는 지갑에서 돈은 빠졌지만 받는 지갑 입금이 실패해 송금 금액이 떠버릴 수 있다.
그래서 코드에서는 송금 상태 머신, COMPENSATION_REQUIRED, 멱등한 환급/취소 API, 재처리 잡을 두고,
운영에서는 보상 대기/실패 건수, 상태 전이 로그, 수동 개입 대상을 확인해야 한다.
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
Saga Compensation And Recovery 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.
