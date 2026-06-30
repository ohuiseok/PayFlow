# Kafka Basics

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

## 핵심 개념

Kafka는 분산 메시징 플랫폼이다. Producer가 Topic에 메시지를 쓰고, Consumer가 Topic에서 메시지를 읽는다. Topic은 Partition으로 나뉘며, 메시지는 Partition 안에서 순서를 가진다.

## PayFlow 연결

PayFlow에는 서로 다른 두 이벤트 흐름이 있다. `transfer-service`의 송금 이벤트는 `ledger-service`가 소비하고, `banking-service`의 Toss 정산 이벤트는 `settlement-service`가 소비한다.

```text
transfer.completed
  -> ledger-service

payment.settlement
  -> settlement-service
```

Kafka를 사용하면 송금 처리와 후속 처리를 분리할 수 있다.

## 실무 포인트

- Topic은 이벤트 의미 기준으로 설계한다.
- Partition key는 순서 보장 단위와 연결된다.
- Consumer Group은 병렬 처리와 관련된다.
- Kafka는 중복 전달 가능성을 고려해야 한다.
- 메시지 스키마 변경 전략이 필요하다.

## 체크 질문

- Topic과 Partition의 차이는 무엇인가
- Kafka 메시지는 어디에서 순서가 보장되는가
- PayFlow에서 Kafka를 사용하는 이유는 무엇인가

## 실무 설계 참고

### 대표 장애 시나리오

ledger-service가 장애일 때 송금 API까지 실패한다.

### 잘못된 구현 예시

~~~text
후속 처리를 모두 동기 HTTP 호출로 처리한다.
~~~

### 좋은 구현 예시

~~~text
transfer.completed와 payment.settlement를 업무 의미별로 분리하고 각 서비스가 자기 토픽을 소비한다.
~~~

### 대안과 선택 이유

서비스 간 REST 호출만으로 후속 처리를 연결하는 방식도 있지만, 원장이나 정산 서비스 장애가 송금 응답에 직접 영향을 준다. PayFlow는 Kafka와 Outbox로 시간적 결합을 낮추고, Consumer 멱등성과 DLQ로 중복/실패를 감당하는 방식이 더 적합하다.

### PayFlow에서 확인할 위치

transfer outbox/ledger consumer, banking settlement outbox/settlement consumer

### 면접에서 설명하기

Kafka는 메시지를 저장하는 큐가 아니라 서비스 간 시간적 결합을 줄이는 로그다.

### 관련 문서

54, 55, 58, 59

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

Kafka Basics 개념은 PayFlow에서 다음 이유로 중요하다.

- Kafka는 PayFlow에서 송금 후 원장 기록과 Toss 승인/취소 후 정산 수집을 비동기로 연결한다.
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
Kafka Basics 개념은 PayFlow에서 송금 완료 이벤트를 원장과 정산 서비스가 독립적으로 소비하게 하기 위해 필요하다.
이 개념이 없으면 동기 호출만 사용하면 ledger-service 장애가 송금 API 응답 장애로 직접 전파된다.
그래서 코드에서는 transfer.completed topic, producer/consumer 분리, consumer group 설정을 사용하고,
운영에서는 topic 발행량, consumer lag, 서비스별 소비 성공률을 확인해야 한다.
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
Kafka Basics 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.

