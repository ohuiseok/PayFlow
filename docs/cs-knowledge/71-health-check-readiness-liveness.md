# Health Check Readiness Liveness

## 핵심 개념

Health Check는 애플리케이션이 정상인지 확인하는 방법이다. Liveness는 프로세스가 살아 있는지, Readiness는 요청을 받을 준비가 되었는지 확인한다.

### Liveness vs Readiness의 근본 차이

Liveness와 Readiness는 목적이 완전히 다르다. 이 둘을 혼동하면 장애가 오히려 악화된다.

**Liveness Probe**는 "프로세스가 살아 있는가"를 확인한다. Kubernetes는 Liveness가 실패하면 컨테이너를 강제로 재시작한다. 따라서 Liveness가 실패한다는 것은 "이 컨테이너는 더 이상 정상적으로 작동할 수 없으니 재시작해야 한다"는 신호다. 데드락, 무한 루프, 메모리 고갈 같은 복구 불가능한 상태에서만 실패해야 한다.

**Readiness Probe**는 "요청을 받을 준비가 되었는가"를 확인한다. Readiness가 실패하면 Kubernetes는 해당 Pod를 Service의 엔드포인트에서 제거해 트래픽을 보내지 않는다. 컨테이너는 계속 실행 중이지만 로드밸런서가 해당 인스턴스로 요청을 전달하지 않는다. DB 연결 풀 고갈, 캐시 워밍업 미완료, 외부 의존성 장애 같은 일시적 상태에 적합하다.

### 내부 동작 메커니즘

Kubernetes는 주기적으로 세 가지 방식으로 헬스체크를 수행한다.

**HTTP GET**: 지정한 경로와 포트로 HTTP 요청을 보내 2xx~3xx 응답이 오면 성공으로 처리한다.

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
  failureThreshold: 3
  timeoutSeconds: 5

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 5
  failureThreshold: 3
```

**TCP Socket**: 지정한 포트에 TCP 연결이 가능한지 확인한다. HTTP를 노출하지 않는 서비스에 적합하다.

**Exec Command**: 컨테이너 내부에서 명령어를 실행해 exit code 0이면 성공으로 처리한다.

주요 파라미터의 의미는 다음과 같다.

- `initialDelaySeconds`: 컨테이너 시작 후 첫 프로브까지 대기 시간. 애플리케이션이 기동되기 전에 프로브가 실행되지 않도록 설정한다.
- `periodSeconds`: 프로브 실행 주기.
- `failureThreshold`: 몇 번 연속 실패해야 조치를 취할지. Liveness는 재시작, Readiness는 엔드포인트 제거.
- `successThreshold`: Readiness에서 몇 번 연속 성공해야 다시 엔드포인트에 추가할지.
- `timeoutSeconds`: 프로브 응답 타임아웃.

### Spring Boot Actuator와의 연동

Spring Boot 2.3+ 에서는 `/actuator/health/liveness`와 `/actuator/health/readiness`를 기본 제공한다.

```yaml
# application.yml
management:
  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true
  endpoint:
    health:
      probes:
        enabled: true
      show-details: always
```

Actuator는 각 헬스 지표를 컴포넌트별로 분리해 관리한다. DB, Redis, Kafka, Disk Space 등 각 의존성의 상태를 개별 컴포넌트로 등록할 수 있다.

```java
// 커스텀 Readiness 체크 예시
@Component
public class KafkaReadinessIndicator implements HealthIndicator {

    private final KafkaTemplate<?, ?> kafkaTemplate;

    @Override
    public Health health() {
        try {
            kafkaTemplate.metrics(); // 메트릭 접근 가능하면 연결됨
            return Health.up()
                .withDetail("kafka", "connected")
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("kafka", "disconnected")
                .withException(e)
                .build();
        }
    }
}
```

### 헬스체크 엔드포인트 설계 원칙

헬스체크 응답은 빠르게 반환되어야 한다. 응답 시간이 `timeoutSeconds`를 초과하면 실패로 처리된다. DB 쿼리를 헬스체크에 포함하려면 가벼운 쿼리(`SELECT 1`)를 사용해야 한다.

헬스체크 자체가 시스템에 부하를 주면 안 된다. 예를 들어 헬스체크가 DB 쿼리 100건을 수행한다면, 100개의 인스턴스가 10초 주기로 헬스체크를 호출할 때 초당 1000건의 불필요한 쿼리가 발생한다.

인증 없이 헬스체크 엔드포인트에 접근할 수 있어야 하지만, 민감한 정보(DB 연결 문자열, API 키, 내부 IP 등)를 응답에 포함하면 안 된다.

### Startup Probe

Kubernetes 1.16+에서 추가된 Startup Probe는 컨테이너가 처음 기동될 때만 사용된다. Startup Probe가 성공하기 전까지는 Liveness/Readiness Probe가 시작되지 않는다. 기동 시간이 길거나 불규칙한 애플리케이션(레거시 Java 앱, 대용량 데이터 로딩)에 유용하다.

```yaml
startupProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  failureThreshold: 30      # 최대 30 * 10초 = 5분 대기
  periodSeconds: 10
```

### 흔한 오해와 함정

**오해 1: DB 연결 실패 = Liveness 실패**

DB가 일시적으로 응답하지 않을 때 Liveness가 실패하면 컨테이너가 재시작된다. 재시작해도 DB 연결이 복구되지 않으면 계속 재시작되고, 모든 인스턴스가 동시에 재시작되면서 장애가 눈덩이처럼 커진다. DB 연결 문제는 Readiness에서만 처리해야 한다.

**오해 2: 헬스체크는 단순히 "200 OK"만 반환하면 된다**

단순히 HTTP 200을 반환하는 헬스체크는 의존성 장애를 감지하지 못한다. DB 연결, Redis 연결, 필수 설정값 존재 여부 등을 실제로 확인해야 의미 있는 헬스체크가 된다.

**오해 3: Readiness 실패하면 트래픽이 줄어든다**

실제로는 Readiness가 실패한 Pod는 Service 엔드포인트에서 제거되므로 해당 Pod로 가는 트래픽이 0이 된다. 남은 정상 Pod들이 전체 트래픽을 받게 된다. Pod 수가 적으면 과부하가 발생할 수 있다.

### Trade-off

헬스체크를 너무 자주(periodSeconds=1) 설정하면 불필요한 요청이 많아지고, 너무 드물게(periodSeconds=60) 설정하면 장애 감지가 늦어진다. failureThreshold를 너무 낮게 설정하면 일시적 네트워크 지연에도 재시작이 발생하고, 너무 높게 설정하면 실제 장애 감지가 늦어진다.

보안 관점에서 헬스체크 엔드포인트는 외부 인터넷에 노출되지 않도록 내부 포트나 별도 관리 포트로 분리하는 것이 좋다.

## PayFlow 연결

PayFlow 서비스가 기동되었더라도 DB, Redis, Kafka 연결이 준비되지 않았으면 요청을 받으면 안 된다. Readiness가 실패해야 Gateway나 로드밸런서가 트래픽을 보내지 않는다.

반면 일시적인 DB 장애 때문에 Liveness가 실패해 계속 재시작되면 오히려 장애가 커질 수 있다.

## 실무 포인트

- Liveness와 Readiness를 구분한다.
- DB 연결은 Readiness에 포함할 수 있다.
- Liveness는 너무 민감하게 만들지 않는다.
- Kafka Consumer 상태도 운영 지표로 본다.
- 헬스체크 응답에 Secret을 노출하지 않는다.

## 체크 질문

- Liveness와 Readiness의 차이는 무엇인가
- DB가 준비되지 않았을 때 Readiness가 실패해야 하는 이유는 무엇인가
- Liveness가 너무 민감하면 어떤 문제가 생기는가

## 실무 설계 참고

### 대표 장애 시나리오

서비스는 떠 있지만 DB 연결이 안 된 상태에서 Gateway가 트래픽을 보낸다.

### 잘못된 구현 예시

~~~text
프로세스가 살아 있으면 요청도 받을 수 있다고 생각한다.
~~~

### 좋은 구현 예시

~~~text
liveness와 readiness를 분리하고 의존성 준비 상태를 readiness에 반영한다.
~~~

### 대안과 선택 이유

장애가 나면 사람이 재시작하거나 배포를 되돌리는 방식도 가능하지만, 결제 시스템에서는 장애 전파 속도가 빠르고 수동 대응은 늦다. PayFlow는 Timeout, Retry 제한, Circuit Breaker, Bulkhead, Graceful Shutdown, 호환 배포를 통해 피해 범위를 자동으로 줄이는 편이 낫다.

### PayFlow에서 확인할 위치

actuator health, gateway route health, docker healthcheck

### 면접에서 설명하기

Liveness는 살아 있음, Readiness는 받을 준비가 됨이다. 둘을 섞으면 재시작 폭풍이 생길 수 있다.

### 관련 문서

28, 70, 73

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

Health Check Readiness Liveness 개념은 PayFlow에서 다음 이유로 중요하다.

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
Health Check Readiness Liveness 개념은 PayFlow에서 살아 있는 프로세스와 트래픽을 받아도 되는 상태를 구분하기 위해 필요하다.
이 개념이 없으면 서비스 프로세스는 떴지만 DB 연결이 준비되지 않아 Gateway가 보내는 요청이 모두 실패할 수 있다.
그래서 코드에서는 liveness/readiness를 분리하고 DB/Kafka 준비 상태를 readiness에 반영하며,
운영에서는 readiness 실패, liveness restart, 기동 직후 오류율을 확인해야 한다.
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
Health Check Readiness Liveness 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.
