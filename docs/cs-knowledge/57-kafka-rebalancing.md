# Kafka Rebalancing

## 핵심 개념

Rebalancing은 Consumer Group 안에서 Partition 할당을 다시 조정하는 과정이다. Consumer가 추가되거나 제거되거나 응답하지 않으면 Rebalancing이 발생한다.

## PayFlow 연결

`ledger-service` Consumer가 재시작되면 Kafka는 Partition을 다시 할당한다. 이 과정에서 잠시 소비가 멈추거나, 처리 중이던 메시지가 다시 처리될 수 있다.

결제 이벤트 Consumer는 Rebalancing을 고려해 중복 처리에 안전해야 한다.

## 실무 포인트

- Consumer 처리가 너무 오래 걸리면 Rebalancing이 발생할 수 있다.
- `max.poll.interval.ms`와 처리 시간을 맞춘다.
- 메시지 처리는 가능한 짧게 유지한다.
- Rebalancing 후 중복 처리를 대비한다.
- 배포 중 Consumer 재시작이 이벤트 처리 지연을 만들 수 있다.

## 체크 질문

- Kafka Rebalancing은 언제 발생하는가
- Rebalancing 중 메시지 처리가 지연될 수 있는 이유는 무엇인가
- Rebalancing과 Consumer 멱등성은 어떤 관계가 있는가

## 실무 설계 보강

### 대표 장애 시나리오

배포 중 rebalance가 발생해 처리 중이던 이벤트가 다시 전달된다.

### 잘못된 구현 예시

~~~text
Consumer 재시작이 메시지 처리에 영향을 주지 않는다고 생각한다.
~~~

### 좋은 구현 예시

~~~text
Consumer 멱등성, graceful shutdown, 처리 시간과 poll interval 조정을 적용한다.
~~~

### 대안과 선택 이유

서비스 간 REST 호출만으로 후속 처리를 연결하는 방식도 있지만, 원장이나 정산 서비스 장애가 송금 응답에 직접 영향을 준다. PayFlow는 Kafka와 Outbox로 시간적 결합을 낮추고, Consumer 멱등성과 DLQ로 중복/실패를 감당하는 방식이 더 적합하다.

### PayFlow에서 확인할 위치

Kafka consumer logs, deployment logs, rebalance metrics

### 면접에서 설명하기

Rebalancing은 Kafka의 정상 동작이지만, 결제 Consumer는 중복 처리를 전제로 해야 한다.

### 관련 문서

55, 56, 60, 70

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

Kafka Rebalancing 개념은 PayFlow에서 다음 이유로 중요하다.

- Kafka는 PayFlow에서 송금 완료 후 원장과 정산을 비동기로 연결하면서 서비스 간 결합도를 낮추기 위해 필요하다.
- Kafka topic/partition/offset, outbox_event, processed_event, DLQ가 이벤트 처리의 기준이다.
- partition key, offset commit, consumer group 설정이 잘못되면 이벤트 순서가 꼬이거나 처리 누락처럼 보일 수 있다.
- topic 설계, key 선택, consumer group 분리, 처리 후 offset commit, consumer 멱등성으로 방어한다.
- 운영에서는 Kafka lag, rebalance 횟수, DLQ 메시지 수, Consumer 처리 시간, processed_event 중복 차단 건수를 본다.

#### 질문에 답하는 방식

좋은 답변은 용어 정의에서 멈추지 않고 다음 순서로 이어져야 한다.

1. 어떤 데이터나 요청을 보호하려는 개념인지 말한다.
2. 그 데이터의 진실의 원천이 어느 서비스에 있는지 말한다.
3. 장애, 중복, 동시성, 지연 중 어떤 상황에서 문제가 생기는지 설명한다.
4. 코드 레벨의 방어 수단과 운영 레벨의 확인 수단을 함께 말한다.

#### PayFlow 예시 답변

```text
Kafka Rebalancing 개념은 PayFlow에서 Consumer 재시작과 배포 중 partition 재할당으로 인한 중복/지연을 감당하기 위해 필요하다.
이 개념이 없으면 rebalance 중 처리하던 송금 이벤트가 다시 전달되어 원장이 중복 생성될 수 있다.
그래서 코드에서는 Consumer 멱등성, 짧은 처리 시간, max.poll 설정, graceful shutdown을 적용하고,
운영에서는 rebalance 횟수, poll interval 초과, 중복 차단 건수를 확인해야 한다.
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
Kafka Rebalancing 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.
