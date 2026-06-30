# Kafka Consumer Group

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

## 핵심 개념

Consumer Group은 같은 목적을 가진 Consumer들의 묶음이다. 하나의 Consumer Group 안에서는 각 Partition이 한 Consumer에게만 할당된다. 서로 다른 Group은 같은 메시지를 각각 독립적으로 소비할 수 있다.

## PayFlow 연결

현재 `ledger-service`는 `transfer.completed`/`transfer.failed`, `settlement-service`는 `payment.settlement`를 소비한다. 서로 다른 토픽이지만 각 서비스는 고유 group을 사용하며, 같은 서비스 인스턴스끼리는 같은 group에서 파티션을 나눠 처리한다.

반대로 `ledger-service` 인스턴스를 여러 개 띄우면 같은 Consumer Group 안에서 Partition을 나눠 처리한다.

## 실무 포인트

- 서비스별로 Consumer Group을 분리한다.
- 같은 Group 안에서는 메시지를 나눠 처리한다.
- Consumer 수가 Partition 수보다 많으면 일부 Consumer는 놀 수 있다.
- Group ID 변경은 처음부터 다시 읽는 결과를 만들 수 있다.

## 체크 질문

- 서로 다른 서비스가 같은 이벤트를 모두 받으려면 Consumer Group을 어떻게 설정해야 하는가
- Consumer 수가 Partition 수보다 많으면 어떤 일이 생기는가
- Group ID를 바꾸면 어떤 영향이 있을 수 있는가

## 실무 설계 참고

### 대표 장애 시나리오

같은 업무의 서로 다른 Consumer가 실수로 같은 group id를 사용해 메시지를 나눠 가져간다.

### 잘못된 구현 예시

~~~text
Consumer group을 단순히 애플리케이션 이름처럼 대충 정한다.
~~~

### 좋은 구현 예시

~~~text
서비스 목적별 group id를 분리하고 같은 서비스 인스턴스끼리만 같은 group을 쓴다.
~~~

### 대안과 선택 이유

서비스 간 REST 호출만으로 후속 처리를 연결하는 방식도 있지만, 원장이나 정산 서비스 장애가 송금 응답에 직접 영향을 준다. PayFlow는 Kafka와 Outbox로 시간적 결합을 낮추고, Consumer 멱등성과 DLQ로 중복/실패를 감당하는 방식이 더 적합하다.

### PayFlow에서 확인할 위치

Kafka consumer config, application.yml, topic monitoring

### 면접에서 설명하기

Consumer Group은 메시지를 나눠 가질 대상을 정한다. 다른 업무는 다른 group이어야 한다.

### 관련 문서

53, 54, 56

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

Kafka Consumer Group 개념은 PayFlow에서 다음 이유로 중요하다.

- Kafka는 PayFlow에서 송금-원장과 Toss-정산을 각각 비동기로 연결하면서 서비스 간 결합도를 낮추기 위해 필요하다.
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
Kafka Consumer Group 개념은 PayFlow에서 같은 업무 Consumer 인스턴스가 파티션을 나눠 처리하고 다른 업무는 독립적으로 소비하게 하기 위해 필요하다.
이 개념이 없으면 독립 처리해야 할 Consumer가 같은 group을 써서 이벤트를 나눠 가져갈 수 있다.
그래서 코드에서는 서비스별 consumer group을 분리하고 인스턴스 수와 partition 수를 맞추며,
운영에서는 group별 lag, assigned partition, consumer idle 상태를 확인해야 한다.
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
Kafka Consumer Group 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.

