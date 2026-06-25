# Observability Logging Metrics Tracing

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

## 핵심 개념

관측성(Observability)은 시스템 내부 상태를 외부에서 얼마나 잘 이해할 수 있는지를 나타내는 능력이다. 단순히 "작동하는가"를 넘어 "왜 이렇게 동작하는가"를 파악할 수 있어야 한다.

관측성의 세 기둥은 로깅(Logging), 메트릭(Metrics), 트레이싱(Tracing)이다. 각각 다른 관점으로 시스템을 바라본다.

### 로깅 (Logging)

로그는 특정 시점에 발생한 이벤트를 기록한다. "무슨 일이 언제 일어났는가"를 기록한다.

**구조화 로그**: 단순 텍스트가 아닌 JSON 형식의 구조화 로그를 사용하면 검색과 집계가 훨씬 쉽다.

```json
{
  "timestamp": "2024-01-15T10:23:45.123Z",
  "level": "ERROR",
  "service": "transfer-service",
  "traceId": "abc123",
  "spanId": "def456",
  "userId": "user-789",
  "transferId": "txn-001",
  "message": "잔액 부족으로 송금 실패",
  "errorCode": "INSUFFICIENT_BALANCE",
  "requestedAmount": 50000,
  "availableBalance": 30000
}
```

**로그 레벨 설계**:
- `ERROR`: 즉각 조치가 필요한 심각한 문제
- `WARN`: 주의가 필요하지만 서비스는 계속 동작
- `INFO`: 주요 비즈니스 이벤트 (송금 요청, 완료, 실패)
- `DEBUG`: 개발/디버깅용. 운영에서는 off

```java
// 좋은 로그 예시: 컨텍스트 포함
log.error("송금 실패 [transferId={}, fromWallet={}, toWallet={}, amount={}, reason={}]",
    transfer.getId(), transfer.getFromWalletId(), transfer.getToWalletId(),
    transfer.getAmount(), e.getMessage());

// 나쁜 로그 예시: 컨텍스트 없음
log.error("Error occurred");
```

**로그 집계**: 분산 시스템에서는 각 서비스의 로그를 중앙에서 수집한다. ELK Stack (Elasticsearch + Logstash + Kibana) 또는 Grafana Loki + Promtail이 대표적이다.

```yaml
# Logback 설정 (JSON 구조화 로그)
<dependency>
  <groupId>net.logstash.logback</groupId>
  <artifactId>logstash-logback-encoder</artifactId>
</dependency>
```

### 메트릭 (Metrics)

메트릭은 시간에 따른 수치 데이터다. "얼마나, 얼마나 빠르게, 얼마나 자주"를 측정한다.

**메트릭 유형**:
- **Counter**: 누적 증가하는 수치. 요청 수, 에러 수, 이벤트 처리 수
- **Gauge**: 현재 값. 현재 연결 수, 큐 크기, 메모리 사용량
- **Histogram**: 값의 분포. 응답 시간, 요청 크기. 백분위수(p50, p90, p99) 계산 가능
- **Summary**: Histogram과 비슷하지만 클라이언트 측에서 백분위수 계산

```java
// Micrometer (Spring Boot Actuator 기반)
@Autowired
MeterRegistry meterRegistry;

// Counter
Counter.builder("transfer.request.total")
    .tag("status", "success")
    .register(meterRegistry)
    .increment();

// Timer (Histogram 포함)
Timer timer = Timer.builder("transfer.processing.time")
    .tag("service", "transfer-service")
    .publishPercentiles(0.5, 0.9, 0.99)
    .register(meterRegistry);

timer.record(() -> processTransfer(request));
```

**USE 방법론**: CPU, 메모리, 네트워크 등 자원별로 Utilization(사용률), Saturation(포화도), Errors(에러)를 측정한다.

**RED 방법론**: 서비스 관점에서 Rate(요청률), Errors(에러율), Duration(응답 시간)을 측정한다. MSA 서비스 모니터링에 적합하다.

**Prometheus + Grafana**: 가장 널리 쓰이는 메트릭 스택이다. Prometheus가 각 서비스의 `/actuator/prometheus` 엔드포인트를 주기적으로 수집(scrape)하고, Grafana에서 대시보드로 시각화한다.

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'transfer-service'
    static_configs:
      - targets: ['transfer-service:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
```

### 분산 트레이싱 (Distributed Tracing)

트레이싱은 하나의 요청이 여러 서비스를 거쳐가는 전체 흐름을 추적한다. "어디서 얼마나 시간이 걸렸는가"를 파악한다.

**핵심 개념**:
- **Trace**: 하나의 요청 전체 흐름. 고유한 Trace ID를 가진다.
- **Span**: 하나의 작업 단위. 서비스 호출, DB 쿼리, Kafka 발행 각각이 Span이 된다. 시작 시간, 종료 시간, 태그, 이벤트를 포함한다.
- **부모-자식 관계**: Span은 부모-자식 계층 구조를 가져 어떤 작업이 다른 작업을 호출했는지 알 수 있다.

```text
Trace: abc123 (전체 흐름)
  └── Span: API Gateway (100ms)
        └── Span: transfer-service (80ms)
              ├── Span: DB 조회 (5ms)
              ├── Span: wallet-service 호출 (60ms)
              │     └── Span: DB 업데이트 (10ms)
              └── Span: Kafka 발행 (5ms)
```

**OpenTelemetry**: 로그, 메트릭, 트레이싱을 표준화하는 CNCF 프로젝트다. Spring Boot에서는 Micrometer Tracing + OpenTelemetry Exporter를 사용한다.

```yaml
# application.yml
management:
  tracing:
    sampling:
      probability: 0.1  # 10%만 샘플링 (트래픽이 많을 때)
spring:
  application:
    name: transfer-service
```

```java
// Trace ID는 MDC에 자동으로 추가되어 로그에 포함됨
// HTTP 헤더 W3C Trace Context: traceparent
// 서비스 간 호출 시 자동으로 전파
```

**Jaeger / Zipkin**: 트레이스를 수집하고 시각화하는 백엔드다. Trace ID로 요청 전체 흐름을 Waterfall 차트로 확인할 수 있다.

### 세 가지 신호의 연결

세 신호가 연결될 때 가장 강력해진다.

```text
1. Grafana에서 p99 응답 시간 급등 감지 (메트릭)
2. 해당 시간대 ERROR 로그 검색 (로그)
3. 에러 로그의 Trace ID로 Jaeger에서 전체 흐름 추적 (트레이싱)
4. 어떤 서비스의 어떤 DB 쿼리에서 60ms → 3000ms로 느려졌는지 파악
```

이 연결을 위해 로그에는 항상 Trace ID를 포함해야 하고, 메트릭에도 서비스/엔드포인트 태그를 붙여야 한다.

### 샘플링 전략

트레이싱은 모든 요청을 기록하면 비용이 크다. 트래픽이 많은 서비스에서는 샘플링이 필요하다.

- **확률적 샘플링**: 전체 요청의 일정 비율(1%, 10%)만 기록
- **레이트 리미팅 샘플링**: 초당 N개의 트레이스만 기록
- **Tail-based 샘플링**: 요청이 완료된 후 에러가 발생했거나 느린 요청은 반드시 기록

결제 시스템에서는 실패 요청은 100% 트레이싱하고, 성공 요청은 일부만 샘플링하는 전략이 적합하다.

### 흔한 오해와 함정

**오해: 로그만 있으면 충분하다**

로그는 개별 이벤트를 기록하지만 시간에 따른 추세나 분포를 보려면 메트릭이 필요하다. 수백만 건의 로그에서 "지난 1시간 동안 p99 응답 시간"을 계산하는 것은 비효율적이다.

**오해: 모든 것을 로깅하면 좋다**

과도한 로깅은 성능을 저하시키고 스토리지 비용을 증가시키며, 정작 중요한 로그를 찾기 어렵게 만든다. 로그 레벨을 적절히 사용하고, 개인정보(이름, 계좌번호, 카드번호)는 로그에 포함하면 안 된다.

**함정: Trace ID가 로그에 없다**

MDC(Mapped Diagnostic Context)에 Trace ID를 설정하지 않으면 로그와 트레이스를 연결할 수 없다. Spring Sleuth나 Micrometer Tracing은 자동으로 MDC를 설정하지만, 비동기 처리(CompletableFuture, @Async)에서는 MDC가 전파되지 않을 수 있다.

### Trade-off

관측성 인프라(ELK, Prometheus, Jaeger 등)는 구축과 운영 비용이 상당하다. 초기에는 단순한 CloudWatch, Datadog 같은 관리형 서비스로 시작하는 것이 현실적이다. 트레이싱 샘플링 비율을 낮추면 비용은 줄지만 희귀한 장애를 놓칠 수 있다.

## PayFlow 연결

송금 요청 하나는 Gateway, transfer, wallet, Kafka, ledger, settlement를 지나갈 수 있다. 장애가 발생하면 어느 단계에서 실패했는지 추적해야 한다.

Correlation ID 또는 Trace ID가 있으면 여러 서비스 로그를 하나의 요청 기준으로 연결할 수 있다.

## 실무 포인트

- 요청 ID를 모든 서비스 로그에 전달한다.
- 송금 상태 변경 로그를 남긴다.
- Kafka lag, Outbox 미발행 건수, 실패 이벤트 수를 지표화한다.
- 에러 로그에는 원인과 식별자를 남긴다.
- 민감 정보는 마스킹한다.

## 체크 질문

- 로그, 메트릭, 트레이싱의 차이는 무엇인가
- MSA에서 Trace ID가 중요한 이유는 무엇인가
- PayFlow에서 꼭 모니터링해야 할 지표는 무엇인가

## 실무 설계 참고

### 대표 장애 시나리오

사용자는 송금 실패를 봤지만 어느 서비스에서 실패했는지 운영자가 찾지 못한다.

### 잘못된 구현 예시

~~~text
서비스별 로그만 있으면 나중에 grep으로 찾을 수 있다고 생각한다.
~~~

### 좋은 구현 예시

~~~text
traceId/correlationId, 구조화 로그, 메트릭, 분산 트레이싱을 모든 경로에 전파한다.
~~~

### 대안과 선택 이유

문제가 생기면 로그를 직접 뒤져 해결하는 방식도 있지만, MSA 결제 흐름에서는 원인 추적이 너무 늦어진다. PayFlow는 테스트, 계약 검증, 관측성, SLO, 알림, 장애 주입을 통해 배포 전후에 시스템을 증명하는 방식이 더 전문적이다.

### PayFlow에서 확인할 위치

gateway logs, transfer/wallet/ledger logs, Kafka headers, metrics dashboard

### 면접에서 설명하기

관측성은 장애가 난 뒤 추측 시간을 줄이는 능력이다. MSA에서는 요청 경로를 이어서 봐야 한다.

### 관련 문서

77, 78, 79

## 질문의 질문으로 더 깊게 보기

### 이 문서에서 잡아야 할 CS 감각

이 구간의 핵심은 운영에서 시스템을 증명하는 방법이다. 테스트는 배포 전의 증명이고, 관측성은 배포 후의 증명이다. 결제 시스템은 "잘 될 것이다"가 아니라 "틀어졌을 때 발견하고 복구할 수 있다"를 보여줘야 한다.

### 질문의 질문

아래 질문들은 답을 외우기 위한 질문이 아니다. 하나씩 답하면서 "왜 그런 설계가 필요한지"를 설명하는 연습을 하기 위한 질문이다.

- 이 개념이 제대로 동작한다는 것을 어떤 지표나 테스트로 증명할 수 있는가?
- 정상 케이스보다 실패 케이스에서 어떤 상태가 남아야 하는가?
- 운영자가 새벽에 알림을 받았을 때 이 문서만 보고 다음 행동을 결정할 수 있는가?
- 이 개념을 적용하면 어떤 문제가 해결되지만, 어떤 비용이나 복잡도가 새로 생기는가?
- 장애가 발생했을 때 이 개념은 문제를 예방하는가, 감지하는가, 복구하는가?
- PayFlow 코드에서 이 개념을 확인하려면 어떤 서비스, 테이블, 로그, 테스트를 봐야 하는가?

### 답안 예시

<details>
<summary>답안 예시 펼치기</summary>

#### 핵심 답변

Observability Logging Metrics Tracing 개념은 PayFlow에서 다음 이유로 중요하다.

- 운영과 테스트 개념은 PayFlow가 실제 장애 상황에서도 기대한 대로 동작하는지 증명하기 위해 필요하다.
- 테스트 결과, 로그, 메트릭, 트레이스, 알림, 장애 대응 기록이 시스템 신뢰성의 근거다.
- 테스트하지 않은 실패 경로는 운영에서 처음 발견되고, 관측성이 없으면 어디서 깨졌는지 알 수 없다.
- 단위/통합/E2E/계약/장애 주입 테스트와 로그, 메트릭, 트레이싱, SLO, 알림 체계로 방어한다.
- 운영에서는 p95/p99 latency, 에러율, 테스트 실패율, 알림 발생 수, Trace별 실패 지점, SLO 위반 시간을 본다.

#### 질문에 답하는 방식

좋은 답변은 용어 정의에서 멈추지 않고 다음 순서로 이어져야 한다.

1. 어떤 데이터나 요청을 보호하려는 개념인지 말한다.
2. 그 데이터의 진실의 원천이 어느 서비스에 있는지 말한다.
3. 장애, 중복, 동시성, 지연 중 어떤 상황에서 문제가 생기는지 설명한다.
4. 코드 레벨의 방어 수단과 운영 레벨의 확인 수단을 함께 말한다.

#### PayFlow 예시 답변

```text
Observability Logging Metrics Tracing 개념은 PayFlow에서 송금 하나가 여러 서비스를 지나갈 때 어디서 실패했는지 추적하기 위해 필요하다.
이 개념이 없으면 사용자는 송금 실패를 보지만 transfer, wallet, Kafka, ledger 중 어느 단계에서 깨졌는지 알 수 없다.
그래서 코드에서는 traceId/correlationId, 구조화 로그, 메트릭, 분산 트레이싱을 적용하고,
운영에서는 trace별 실패 지점, Outbox 적체, Kafka lag, 서비스별 에러율을 확인해야 한다.
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
Observability Logging Metrics Tracing 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.


