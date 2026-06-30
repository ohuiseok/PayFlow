# Message Delivery Semantics

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

## 핵심 개념

### 세 가지 전달 보장 모델의 실제 동작 방식

메시지 전달 보장(Message Delivery Semantics)은 Producer가 보낸 메시지가 Consumer에게 얼마나 신뢰성 있게 전달되는지를 정의하는 계약이다. 이 계약은 네트워크 장애, 프로세스 재시작, 브로커 장애 같은 불확실한 환경에서 시스템이 어떻게 동작하는지를 결정한다.

**At-most-once (최대 한 번)**

Producer가 메시지를 브로커에 전송하고 응답을 기다리지 않는다. Consumer는 메시지를 받자마자 오프셋을 커밋하고 처리를 시작한다. 처리 도중 Consumer가 죽으면 그 메시지는 영원히 사라진다. 구현은 가장 단순하고 처리량은 높지만 유실 가능성이 있다.

Kafka에서 이를 구현하면 `acks=0` (Producer 설정), Consumer에서 메시지 수신 즉시 `commitSync()`를 호출하는 방식이다.

**At-least-once (최소 한 번)**

Producer는 브로커 응답을 받을 때까지 재전송한다. Consumer는 메시지를 완전히 처리한 뒤에 오프셋을 커밋한다. 처리 완료 전에 Consumer가 죽으면 재시작 후 같은 메시지를 다시 읽는다. 유실은 없지만 중복이 발생할 수 있다.

```java
// Kafka At-least-once Consumer 패턴
while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
    for (ConsumerRecord<String, String> record : records) {
        processRecord(record);  // 먼저 처리
    }
    consumer.commitSync();  // 처리 완료 후 커밋
}
```

처리 직후, 커밋 직전에 프로세스가 죽으면 재시작 시 같은 메시지를 다시 받는다. 이것이 중복의 근원이다.

**Exactly-once (정확히 한 번)**

이론적으로 완벽하지만 실제로는 범위와 조건을 엄밀히 따져야 한다. Kafka에서 Exactly-once는 두 가지 레벨이 있다.

- **Kafka 내부 Exactly-once**: 동일 Kafka 클러스터 내에서 Producer → Topic → Consumer → Topic 흐름에서의 정확히 한 번. Kafka Transactions API와 Idempotent Producer로 구현한다.
- **End-to-end Exactly-once**: 외부 DB, 외부 시스템과 조합하면 Kafka 내부 보장만으로는 부족하다. Consumer가 Kafka 레코드를 처리하고 외부 DB에 쓰는 부분까지 원자적으로 만들려면 별도 설계가 필요하다.

```java
// Kafka Idempotent Producer 설정
Properties props = new Properties();
props.put("enable.idempotence", true);
props.put("acks", "all");
props.put("max.in.flight.requests.per.connection", 5);
props.put("retries", Integer.MAX_VALUE);
```

### 왜 At-least-once가 결제 시스템의 현실적 선택인가

결제 이벤트에서 유실과 중복 중 무엇이 더 위험한지 따져야 한다.

**유실의 경우**: 송금 완료 이벤트가 사라지면 원장이 생성되지 않는다. 사용자 잔액은 차감됐는데 원장 기록이 없는 상태가 된다. 이 불일치는 자동으로 복구되지 않고 운영자가 발견해서 수동으로 수정해야 한다.

**중복의 경우**: 같은 이벤트가 두 번 오면 Consumer가 이를 감지하고 두 번째를 무시할 수 있다. `processed_event` 테이블의 유니크 제약, DB의 유니크 키 등으로 자동 방어가 가능하다.

따라서 At-least-once + Consumer 멱등성 조합이 결제 시스템에서 가장 현실적이고 안전한 선택이다.

### 오프셋 관리와 중복의 관계

Kafka Consumer는 자신이 어디까지 읽었는지를 오프셋으로 기록한다. 오프셋 커밋 타이밍이 전달 보장 모델을 결정한다.

```
메시지 수신 → [커밋] → 처리  : At-most-once (처리 전 죽으면 유실)
메시지 수신 → 처리 → [커밋]  : At-least-once (커밋 전 죽으면 재처리)
```

`enable.auto.commit=true`이면 Kafka가 백그라운드에서 주기적으로 커밋하는데, 이때 처리 완료 여부와 무관하게 커밋이 발생해 At-most-once 동작을 할 수 있다. 결제 시스템에서는 반드시 `enable.auto.commit=false`로 설정하고 처리 완료 후 수동 커밋해야 한다.

### 흔한 오해와 함정

**"Kafka는 Exactly-once를 지원한다"**는 말을 들으면 그 범위를 반드시 확인해야 한다. Kafka Streams나 Kafka Transactions를 사용하더라도, Consumer가 DB에 쓰거나 외부 API를 호출하는 부분은 Kafka의 Exactly-once 보장 범위 밖이다.

**Consumer Group Rebalance**도 중복의 원인이 된다. 한 Consumer가 메시지를 처리 중에 Rebalance가 발생하면, 커밋되지 않은 오프셋부터 다른 Consumer가 다시 읽기 시작한다.

**Producer 재전송**도 중복을 만든다. `acks=all`이고 브로커가 메시지를 저장했지만 응답 전에 네트워크가 끊기면, Producer는 재전송한다. Idempotent Producer(`enable.idempotence=true`)를 쓰면 Producer 레벨에서 중복 전송을 막을 수 있다.

### 성능과 정합성의 trade-off

| 모델 | 처리량 | 유실 위험 | 중복 위험 | 구현 복잡도 |
|---|---|---|---|---|
| At-most-once | 높음 | 있음 | 없음 | 낮음 |
| At-least-once | 중간 | 없음 | 있음 | 중간 |
| Exactly-once | 낮음 | 없음 | 없음 (범위 내) | 높음 |

Exactly-once는 성능 비용이 있다. Kafka Transactions는 2-phase commit과 유사한 오버헤드가 발생하며 처리량이 감소할 수 있다. 결제 시스템에서는 At-least-once + 멱등성이 성능과 안전성의 균형을 더 잘 맞춘다.

## PayFlow 연결

PayFlow에서는 이벤트 유실보다 중복 처리가 더 다루기 쉽다. 따라서 Kafka Consumer는 At-least-once를 전제로 설계하고, 중복 이벤트를 멱등하게 처리하는 방식이 현실적이다.

원장 기록은 같은 이벤트가 두 번 와도 한 번만 기록되어야 한다.

## 실무 포인트

- At-least-once는 중복을 허용한다.
- Exactly-once는 범위와 조건을 정확히 이해해야 한다.
- 결제 시스템에서는 유실 방지가 특히 중요하다.
- Consumer 멱등성과 유니크 제약이 필요하다.
- 실패 메시지를 추적해야 한다.

## 체크 질문

- At-most-once와 At-least-once의 차이는 무엇인가
- PayFlow에서 중복보다 유실이 더 위험한 이유는 무엇인가
- Exactly-once라는 말을 들을 때 확인해야 할 것은 무엇인가

## 실무 설계 참고

### 대표 장애 시나리오

at-most-once 처리로 송금 완료 이벤트가 유실되어 원장이 생성되지 않는다.

### 잘못된 구현 예시

~~~text
Exactly-once라는 말을 듣고 애플리케이션 중복 방어가 필요 없다고 생각한다.
~~~

### 좋은 구현 예시

~~~text
at-least-once를 기본으로 보고 Consumer 멱등성과 unique constraint를 둔다.
~~~

### 대안과 선택 이유

PayFlow는 transfer→ledger와 banking→settlement 흐름에서 Outbox와 Consumer unique 제약을 조합한다. DLT는 ledger에는 적용되어 있지만 settlement에는 아직 없다.

### PayFlow에서 확인할 위치

Kafka producer/consumer config, ledger processed_event, outbox table

### 면접에서 설명하기

결제 이벤트는 유실보다 중복을 선택하고 중복을 제어하는 쪽이 현실적이다.

### 관련 문서

56, 59, 60

## 질문의 질문으로 더 깊게 보기

### 이 문서에서 잡아야 할 CS 감각

이 구간의 핵심은 메시징 시스템이 비동기성과 신뢰성을 동시에 다룬다는 점이다. Kafka를 쓰면 결합도는 낮아지지만, 중복, 순서, 지연, 재처리 문제가 생긴다. 이벤트 기반 구조는 Consumer 멱등성이 있을 때 비로소 안전해진다.

### 질문의 질문

아래 질문들은 답을 외우기 위한 질문이 아니다. 하나씩 답하면서 "왜 그런 설계가 필요한지"를 설명하는 연습을 하기 위한 질문이다.

- 메시지가 유실되는 것과 중복 처리되는 것 중 PayFlow에서는 무엇이 더 위험한가?
- 같은 이벤트가 두 번 왔을 때 DB 제약조건만으로 충분히 막을 수 있는가?
- 이벤트 처리 지연이 길어질 때 사용자에게 보여주는 상태는 무엇이어야 하는가?
- 이 개념을 적용하면 어떤 문제가 해결되지만, 어떤 비용이나 복잡도가 새로 생기는가?
- 장애가 발생했을 때 이 개념은 문제를 예방하는가, 감지하는가, 복구하는가?
- PayFlow 코드에서 이 개념을 확인하려면 어떤 서비스, 테이블, 로그, 테스트를 봐야 하는가?

### 답안 예시

<details>
<summary>답안 예시 펼치기</summary>

#### 핵심 답변

Message Delivery Semantics 개념은 PayFlow에서 다음 이유로 중요하다.

- Kafka와 메시징 개념은 PayFlow에서 송금-원장과 Toss-정산을 느슨하게 연결하면서도 이벤트를 잃지 않기 위해 필요하다.
- Kafka topic/partition/offset, outbox_event, processed_event, DLQ가 이벤트 처리의 기준이다.
- 이벤트 유실, 중복 소비, 순서 뒤바뀜, Consumer lag, DLQ 적체가 원장 누락이나 중복 기록으로 이어질 수 있다.
- partition key 설계, 처리 후 offset commit, Outbox, processed_event 유니크 제약, DLQ와 재처리 도구로 방어한다.
- 운영에서는 Kafka lag, rebalance 횟수, DLQ 메시지 수, Consumer 처리 시간, processed_event 중복 차단 건수를 본다.

#### 질문에 답하는 방식

좋은 답변은 용어 정의에서 멈추지 않고 다음 순서로 이어져야 한다.

1. 어떤 데이터나 요청을 보호하려는 개념인지 말한다.
2. 그 데이터의 진실의 원천이 어느 서비스에 있는지 말한다.
3. 장애, 중복, 동시성, 지연 중 어떤 상황에서 문제가 생기는지 설명한다.
4. 코드 레벨의 방어 수단과 운영 레벨의 확인 수단을 함께 말한다.

#### PayFlow 예시 답변

```text
Message Delivery Semantics 개념은 PayFlow에서 이벤트 유실과 중복 사이의 trade-off를 이해하고 안전한 전달 모델을 선택하기 위해 필요하다.
이 개념이 없으면 at-most-once 처리에서 송금 원장이나 Toss 정산 이벤트가 유실될 수 있다.
그래서 코드에서는 at-least-once를 전제로 Consumer 멱등성과 유니크 제약, 재처리를 설계하고,
운영에서는 유실 의심 이벤트, 중복 수신, 처리 실패 후 재시도 결과를 확인해야 한다.
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
Message Delivery Semantics 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.

