# Dead Letter Queue

## 핵심 개념

### DLQ가 필요한 이유: Poison Pill 문제

Kafka에서 Consumer는 파티션의 메시지를 순서대로 처리한다. 특정 메시지 처리에 계속 실패하면 그 메시지를 처리할 때까지 같은 파티션의 뒤 메시지들을 처리할 수 없다. 이런 메시지를 "Poison Pill(독약)"이라고 부른다.

```
파티션 상태:
[메시지A(처리완료)] [메시지B(실패중)] [메시지C(대기)] [메시지D(대기)] ...

메시지B가 계속 실패하면 C, D는 아무리 정상이어도 처리되지 않음
```

재시도를 무한히 하면 Consumer lag이 계속 증가하고, 정상 메시지들이 모두 지연된다. DLQ는 이 Poison Pill을 격리해서 정상 메시지 처리를 계속하게 하는 장치다.

### 재시도 가능한 오류와 불가능한 오류 구분

DLQ로 보내기 전에 오류 유형을 반드시 구분해야 한다.

**재시도 가능한 오류 (Transient Error)**
- 일시적 네트워크 오류
- DB 커넥션 타임아웃
- 외부 서비스 일시 장애
- 데드락으로 인한 트랜잭션 실패

이 경우는 재시도하면 성공할 수 있으므로 즉시 DLQ로 보내면 안 된다.

**재시도 불가능한 오류 (Permanent Error)**
- 메시지 스키마 오류 (필수 필드 누락, 잘못된 타입)
- 비즈니스 규칙 위반 (존재하지 않는 transferId 참조)
- DB 유니크 제약 위반 (이미 처리된 이벤트의 중복이 아닌 진짜 데이터 오류)
- 역직렬화 오류

이 경우는 재시도해도 동일하게 실패하므로 DLQ로 즉시 격리해야 한다.

```java
@KafkaListener(topics = "transfer-completed")
public void handleTransferCompleted(ConsumerRecord<String, String> record) {
    try {
        TransferCompletedEvent event = deserialize(record.value());
        processEvent(event);
    } catch (JsonParseException e) {
        // 역직렬화 실패: 재시도해도 무의미 → 즉시 DLQ
        dlqService.sendToDlq(record, e, "DESERIALIZATION_ERROR");
    } catch (TransferNotFoundException e) {
        // 비즈니스 오류: 재시도해도 무의미 → 즉시 DLQ
        dlqService.sendToDlq(record, e, "BUSINESS_ERROR");
    } catch (DatabaseConnectionException e) {
        // 일시적 오류: 재시도 후 실패 시 DLQ
        throw e;  // Kafka가 재시도하도록 예외를 던짐
    }
}
```

### 재시도 정책과 DLQ 연동

Spring Kafka의 `SeekToCurrentErrorHandler`나 `DefaultErrorHandler`를 활용한다.

```java
@Bean
public DefaultErrorHandler errorHandler(KafkaTemplate<String, String> template) {
    // DLQ 발행자 설정
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
        (record, exception) -> new TopicPartition(record.topic() + ".DLQ", -1)
    );

    // 재시도 정책: 1초, 2초, 4초 간격으로 3회 재시도 후 DLQ
    ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
    backOff.setInitialInterval(1000L);
    backOff.setMultiplier(2.0);

    return new DefaultErrorHandler(recoverer, backOff);
}
```

### DLQ 메시지 구조와 메타데이터

DLQ 메시지에는 원본 메시지뿐 아니라 실패 원인 메타데이터가 포함되어야 운영자가 분석할 수 있다.

```json
{
  "originalTopic": "transfer-completed",
  "originalPartition": 2,
  "originalOffset": 12345,
  "originalKey": "transfer-abc-123",
  "originalPayload": "{ ... }",
  "failureReason": "BUSINESS_ERROR",
  "exceptionClass": "com.payflow.TransferNotFoundException",
  "exceptionMessage": "Transfer not found: transfer-abc-123",
  "failedAt": "2026-05-30T10:15:30Z",
  "retryCount": 3
}
```

### DLQ 재처리 전략

DLQ는 실패를 저장하는 공간이지만, 결국 재처리되거나 수동으로 처리되어야 한다.

**자동 재처리**: DLQ 메시지를 주기적으로 읽어 원래 토픽으로 다시 발행한다. 원인이 해결된 후 자동으로 처리된다. 단, 재처리 결과가 여전히 실패하면 무한 루프가 생기지 않도록 재처리 횟수 제한이 필요하다.

**수동 재처리**: 운영자가 원인을 분석하고 데이터를 수정한 뒤 수동으로 재처리한다. 비즈니스 오류의 경우 적합하다.

**폐기**: 재처리가 불가능하거나 불필요한 메시지는 폐기하고 로그로만 남긴다. 테스트 이벤트나 만료된 이벤트가 해당된다.

### 민감 데이터와 보안

결제 이벤트에는 계좌번호, 금액, 개인정보가 포함될 수 있다. DLQ는 원본 메시지를 그대로 저장하므로 접근 제어가 중요하다.

- DLQ 토픽 접근 권한을 운영자로 제한한다.
- DLQ 메시지 보존 기간을 일반 토픽보다 짧게 설정한다.
- 민감 필드를 마스킹한 후 DLQ에 저장하는 것도 고려한다.

### 흔한 오해와 함정

**"모든 실패를 즉시 DLQ로 보내면 빠르게 처리된다"**: 재시도 가능한 일시적 오류도 DLQ로 보내면 나중에 수동 처리 부담이 커진다. 일시 장애가 회복되면 스스로 해결될 메시지들을 모두 수동으로 재처리해야 한다.

**"DLQ가 있으면 안심이다"**: DLQ는 이벤트를 저장하지만 비즈니스 처리가 완료된 것이 아니다. DLQ가 계속 쌓이는데 재처리가 안 되면 원장 누락이 누적된다. 반드시 모니터링과 알림, 재처리 도구가 함께 있어야 한다.

## PayFlow 연결

`ledger-service`가 특정 송금 이벤트를 계속 처리하지 못한다면 해당 메시지를 DLQ로 보내고 알림을 발생시킬 수 있다.

예를 들어 이벤트 스키마가 잘못되었거나, 필수 데이터가 누락되었거나, DB 제약조건에 계속 걸릴 수 있다.

## 실무 포인트

- 재시도 가능한 오류와 불가능한 오류를 구분한다.
- DLQ 메시지는 운영자가 볼 수 있어야 한다.
- DLQ 재처리 도구가 필요하다.
- 민감 데이터가 DLQ에 저장되는지 확인한다.
- DLQ가 쌓이는 속도를 모니터링한다.

## 체크 질문

- DLQ는 왜 필요한가
- 모든 실패 메시지를 즉시 DLQ로 보내면 안 되는 이유는 무엇인가
- DLQ 메시지를 재처리할 때 주의할 점은 무엇인가

## 실무 설계 참고

### 대표 장애 시나리오

깨진 이벤트 하나 때문에 Consumer가 같은 메시지에서 계속 실패한다.

### 잘못된 구현 예시

~~~text
무한 재시도로 언젠가 성공할 것이라고 생각한다.
~~~

### 좋은 구현 예시

~~~text
재시도 가능한 오류와 불가능한 오류를 구분하고 DLQ와 재처리 도구를 둔다.
~~~

### 대안과 선택 이유

서비스 간 REST 호출만으로 후속 처리를 연결하는 방식도 있지만, 원장이나 정산 서비스 장애가 송금 응답에 직접 영향을 준다. PayFlow는 Kafka와 Outbox로 시간적 결합을 낮추고, Consumer 멱등성과 DLQ로 중복/실패를 감당하는 방식이 더 적합하다.

### PayFlow에서 확인할 위치

Kafka consumer error handler, DLQ topic/table, alerting

### 면접에서 설명하기

DLQ는 실패를 숨기는 곳이 아니라 정상 흐름을 보호하고 운영자가 복구할 수 있게 하는 격리 공간이다.

### 관련 문서

58, 60, 77

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

Dead Letter Queue 개념은 PayFlow에서 다음 이유로 중요하다.

- Kafka와 메시징 개념은 PayFlow에서 송금 완료 이후 원장과 정산을 느슨하게 연결하면서도 이벤트를 잃지 않기 위해 필요하다.
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
Dead Letter Queue 개념은 PayFlow에서 계속 실패하는 이벤트가 정상 이벤트 처리를 막지 않게 격리하기 위해 필요하다.
이 개념이 없으면 잘못된 payload 하나가 Consumer를 반복 실패시켜 뒤의 송금 완료 이벤트 처리까지 지연시킬 수 있다.
그래서 코드에서는 재시도 횟수 제한, DLQ 저장, 원인 기록, 재처리 도구를 적용하고,
운영에서는 DLQ 적재량, 재처리 성공률, 동일 원인 실패 빈도를 확인해야 한다.
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
Dead Letter Queue 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.
