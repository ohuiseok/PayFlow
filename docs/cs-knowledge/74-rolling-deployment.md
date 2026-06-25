# Rolling Deployment

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

## 핵심 개념

Rolling Deployment는 여러 인스턴스를 한 번에 모두 교체하지 않고, 일부씩 순차적으로 새 버전으로 바꾸는 배포 방식이다.

무중단 배포에 유리하지만, 배포 중 구버전과 신버전이 동시에 존재한다.

### 동작 원리

인스턴스가 4개인 서비스에 Rolling 배포를 적용하면 다음과 같이 진행된다.

```text
초기 상태:
  [v1] [v1] [v1] [v1]

1단계 (25% 교체):
  [v2] [v1] [v1] [v1]  <- v2 배포 중

2단계 (50% 교체):
  [v2] [v2] [v1] [v1]

3단계 (75% 교체):
  [v2] [v2] [v2] [v1]

완료:
  [v2] [v2] [v2] [v2]
```

각 단계에서 기존 인스턴스를 종료하기 전에 새 인스턴스가 Readiness 체크를 통과해야 다음 단계로 진행한다. 이를 통해 서비스 가용성을 유지한다.

### Kubernetes Rolling Update 설정

```yaml
apiVersion: apps/v1
kind: Deployment
spec:
  replicas: 4
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1        # 최대 추가 생성 허용 수 (4+1=5개까지 가능)
      maxUnavailable: 0  # 동시에 종료 가능한 최대 수 (0이면 항상 4개 이상 유지)
```

**maxSurge**: 배포 중 원래 replica 수를 초과해 생성할 수 있는 Pod 수. `maxSurge: 1`이면 4개 + 1개 = 최대 5개 Pod가 동시에 실행된다. 이 값이 클수록 배포가 빠르지만 자원이 더 필요하다.

**maxUnavailable**: 배포 중 동시에 사용 불가능한 Pod의 최대 수. `maxUnavailable: 0`이면 항상 원래 replica 수만큼 정상 Pod가 유지된다. `maxUnavailable: 1`이면 배포 중 최소 3개는 정상 상태를 유지한다.

배포 속도를 높이려면 `maxSurge`를 늘리거나 `maxUnavailable`을 늘리면 되지만, 자원 비용이나 일시적 가용성 감소를 감수해야 한다.

### 배포 중 버전 공존 문제

Rolling 배포에서 가장 중요한 과제는 구버전과 신버전이 동시에 실행되는 시간 동안 호환성을 유지하는 것이다.

**API 호환성**: 신버전에서 응답 필드를 제거하거나 형식을 바꾸면, 구버전 클라이언트가 그 응답을 처리할 수 없다. 항상 하위 호환 방식으로 변경해야 한다.

```text
안전한 변경:
  - 새 필드 추가
  - 필드의 의미 확장 (좁히기는 위험)
  - 새 API 엔드포인트 추가

위험한 변경:
  - 기존 필드 삭제
  - 필드 타입 변경
  - 기존 API 동작 변경
```

**DB 스키마 호환성**: 신버전이 새 컬럼에 데이터를 쓰기 시작했을 때, 구버전이 그 컬럼을 모르면 데이터가 누락되거나 쿼리가 실패할 수 있다. 구버전이 새 컬럼을 무시하도록 처리해야 한다.

**이벤트/메시지 호환성**: Kafka 메시지에 새 필드가 추가되었을 때, 구버전 컨슈머가 알 수 없는 필드를 무시하도록 Jackson의 `FAIL_ON_UNKNOWN_PROPERTIES: false` 등을 설정해야 한다.

### 롤백

Kubernetes에서 Rolling 배포를 롤백하면 반대 방향으로 Rolling Update가 수행된다.

```bash
kubectl rollout undo deployment/payflow-transfer-service
kubectl rollout status deployment/payflow-transfer-service
```

배포 이력을 보고 특정 버전으로 롤백할 수 있다.

```bash
kubectl rollout history deployment/payflow-transfer-service
kubectl rollout undo deployment/payflow-transfer-service --to-revision=3
```

롤백 속도는 새 버전 배포 속도와 동일하다. 즉각 롤백이 필요하면 Blue-Green이 더 적합하다.

### 헬스체크와의 연동

Rolling 배포는 Readiness Probe를 기반으로 동작한다. 새 Pod의 Readiness가 통과되지 않으면 다음 단계로 진행하지 않는다. 따라서 Readiness Probe가 제대로 설정되지 않으면 오동작 상태의 서버로 트래픽이 전달될 수 있다.

```yaml
# 배포 완료 조건: Readiness 통과 후 최소 안정 시간
spec:
  minReadySeconds: 30  # Readiness 통과 후 30초 추가 대기
```

`minReadySeconds`는 Readiness 통과 직후 발생할 수 있는 초기 오류(JVM Warm-up 미완료, 캐시 미충전 등)를 방지하기 위한 추가 안정화 시간이다.

### 배포 일시정지와 재개

배포 중간에 모니터링을 위해 일시정지할 수 있다.

```bash
# 배포 일시정지
kubectl rollout pause deployment/payflow-transfer-service

# 메트릭, 에러율 등 확인 후 재개
kubectl rollout resume deployment/payflow-transfer-service
```

이 기능을 활용하면 50% 배포 후 에러율을 확인하고 이상이 없으면 나머지 50%를 진행하는 단계적 검증이 가능하다.

### 흔한 오해와 함정

**오해: Rolling 배포는 언제나 안전하다**

배포 중 구/신버전이 공존하는 시간 동안 호환성이 깨지면 오류가 발생한다. 코드만 하위 호환이 되어도 DB 마이그레이션이 구버전과 맞지 않으면 문제가 생긴다. "Rolling 배포니까 무조건 안전"이라는 생각은 위험하다.

**함정: maxUnavailable: 1로 인한 용량 부족**

인스턴스 4개 중 1개가 교체 중이면 3개로 100% 트래픽을 처리해야 한다. 인스턴스당 용량 여유가 없다면 배포 중 성능 저하나 오류가 발생한다. 각 인스턴스는 피크 트래픽의 150% 이상을 처리할 수 있어야 안전하다.

**함정: 오래된 Revision 누적**

Kubernetes는 배포 이력을 `revisionHistoryLimit`만큼 유지한다. 기본값은 10이다. 이 값이 너무 크면 쓸모없는 ReplicaSet이 많이 쌓이고, 너무 작으면 롤백 가능한 버전이 제한된다.

### Trade-off

Rolling 배포는 추가 인프라 비용 없이 무중단 배포가 가능하다는 장점이 있다. 하지만 배포 중 버전 공존 문제와 느린 롤백이 단점이다. 서비스 API와 데이터 계약이 엄격하게 하위 호환을 유지하는 팀에 적합하다.

## PayFlow 연결

PayFlow 서비스가 여러 인스턴스로 운영된다면 rolling 배포 중 API 계약과 이벤트 스키마가 양쪽 버전에서 모두 호환되어야 한다.

예를 들어 `transfer-service` 새 버전이 새로운 이벤트 필드를 발행해도 구버전 `ledger-service`가 처리할 수 있어야 한다.

## 실무 포인트

- 하위 호환 가능한 변경을 우선한다.
- 이벤트 스키마 변경에 주의한다.
- Readiness가 성공한 인스턴스만 트래픽을 받게 한다.
- 배포 중 Consumer Rebalancing을 고려한다.
- 롤백 가능한 단계를 유지한다.

## 체크 질문

- Rolling 배포 중 구버전과 신버전이 공존한다는 것은 어떤 의미인가
- 이벤트 스키마 변경이 위험한 이유는 무엇인가
- Readiness가 rolling 배포에서 중요한 이유는 무엇인가

## 실무 설계 참고

### 대표 장애 시나리오

Rolling 배포 중 신버전 이벤트를 구버전 Consumer가 처리하지 못한다.

### 잘못된 구현 예시

~~~text
한 번에 모두 바뀐다고 가정하고 호환성을 고려하지 않는다.
~~~

### 좋은 구현 예시

~~~text
구버전과 신버전이 공존해도 동작하도록 API와 이벤트 스키마를 하위 호환으로 바꾼다.
~~~

### 대안과 선택 이유

장애가 나면 사람이 재시작하거나 배포를 되돌리는 방식도 가능하지만, 결제 시스템에서는 장애 전파 속도가 빠르고 수동 대응은 늦다. PayFlow는 Timeout, Retry 제한, Circuit Breaker, Bulkhead, Graceful Shutdown, 호환 배포를 통해 피해 범위를 자동으로 줄이는 편이 낫다.

### PayFlow에서 확인할 위치

deployment logs, Kafka consumer schema handling, readiness

### 면접에서 설명하기

Rolling 배포의 본질은 공존이다. 공존 가능한 계약이 없으면 무중단이 아니다.

### 관련 문서

06, 72, 81

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

Rolling Deployment 개념은 PayFlow에서 다음 이유로 중요하다.

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
Rolling Deployment 개념은 PayFlow에서 구버전과 신버전 서비스가 동시에 떠 있어도 API와 이벤트 계약이 깨지지 않게 하기 위해 필요하다.
이 개념이 없으면 신버전 transfer-service가 새 이벤트 필드를 필수로 보내 구버전 ledger-service가 역직렬화에 실패할 수 있다.
그래서 코드에서는 하위 호환 이벤트/응답 스키마, readiness 기반 순차 교체, 배포 순서 관리를 적용하고,
운영에서는 배포 중 오류율, consumer 역직렬화 실패, rebalance 횟수를 확인해야 한다.
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
Rolling Deployment 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.

