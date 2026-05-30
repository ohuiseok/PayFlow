# Alerting And Incident Response

## 핵심 개념

알림(Alerting)과 장애 대응(Incident Response)은 시스템 이상을 빠르게 감지하고, 원인을 파악해 복구하는 운영 체계다. 좋은 알림은 행동 가능한 정보를 제공해야 한다.

### 알림의 종류: Symptom-based vs Cause-based

**Cause-based 알림**은 원인에 대한 알림이다. "CPU가 90%를 넘었다", "DB 커넥션 풀이 80%다"가 해당된다. 이런 알림은 많은 경우 거짓 양성(False Positive)을 유발한다. CPU가 잠깐 90%여도 서비스에 문제가 없을 수 있고, 반대로 CPU는 낮은데 응답이 느릴 수 있다.

**Symptom-based 알림**은 사용자가 경험하는 증상에 대한 알림이다. "에러율이 1%를 넘었다", "p99 응답 시간이 3초를 넘었다"가 해당된다. 사용자 영향이 실제로 발생할 때만 알림이 오므로 더 행동 가능하다.

좋은 알림 체계는 Symptom-based 알림을 기본으로 하고, Cause-based 알림은 티켓으로 처리한다.

### 알림 설계 원칙

**알림은 행동 가능해야 한다**: 알림을 받았을 때 담당자가 즉각 취해야 할 행동이 명확해야 한다. "메모리가 높다"는 알림은 무엇을 해야 하는지 불명확하다. "transfer-service Pod 3개 중 2개가 OOMKilled, 즉시 메모리 제한 확인 필요"가 더 좋다.

**알림은 드물어야 한다**: 알림이 너무 많으면 알림 피로(Alert Fatigue)가 생긴다. 담당자가 알림을 무시하기 시작하면 진짜 장애를 놓친다. 불필요한 알림은 과감히 제거하거나 낮은 심각도로 내려야 한다.

**알림은 빠르게 와야 한다**: 장애 감지부터 알림까지의 지연(MTTD: Mean Time To Detect)이 짧을수록 복구 시간이 짧아진다.

### Prometheus Alertmanager 구성

```yaml
# Prometheus 알림 규칙
groups:
  - name: payflow.rules
    rules:
      # 에러율 알림 (5분 평균)
      - alert: HighErrorRate
        expr: |
          sum(rate(http_requests_total{status=~"5.."}[5m])) by (service)
          /
          sum(rate(http_requests_total[5m])) by (service)
          > 0.01
        for: 2m  # 2분 지속될 때만 알림
        labels:
          severity: critical
        annotations:
          summary: "{{ $labels.service }} 에러율 {{ $value | humanizePercentage }}"
          description: "5분 평균 에러율이 1%를 초과했습니다."
          runbook: "https://wiki.payflow/runbooks/high-error-rate"

      # p99 응답 시간 알림
      - alert: HighLatency
        expr: |
          histogram_quantile(0.99,
            sum(rate(http_request_duration_seconds_bucket[5m])) by (service, le)
          ) > 2
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "{{ $labels.service }} p99 응답 시간 {{ $value }}s"
```

```yaml
# Alertmanager 라우팅
route:
  receiver: 'default'
  routes:
    - match:
        severity: critical
      receiver: 'pagerduty'  # 즉시 호출
    - match:
        severity: warning
      receiver: 'slack'       # Slack 알림

receivers:
  - name: 'pagerduty'
    pagerduty_configs:
      - service_key: '<key>'
  - name: 'slack'
    slack_configs:
      - channel: '#alerts'
        text: '{{ range .Alerts }}{{ .Annotations.summary }}{{ end }}'
```

### 장애 대응 프로세스 (Incident Response)

**MTTD, MTTR, MTBF**

- **MTTD (Mean Time To Detect)**: 장애 발생부터 감지까지 시간. 좋은 알림 체계로 줄인다.
- **MTTR (Mean Time To Recover)**: 감지부터 복구까지 시간. 런북과 자동화로 줄인다.
- **MTBF (Mean Time Between Failures)**: 장애 발생 사이의 평균 시간. 시스템 안정성 지표.

**장애 대응 단계**:

```text
1. 감지 (Detection)
   - 알림 수신 또는 사용자 신고
   - 장애 규모와 영향 파악

2. 대응 (Response)
   - 담당자 지정 (Incident Commander)
   - 상황 공유 채널 개설 (Slack #incident-2024-01-15)
   - 고객 공지 (status page 업데이트)

3. 완화 (Mitigation)
   - 즉각 완화 조치 (롤백, 트래픽 차단, 기능 플래그 off)
   - 근본 원인 분석은 나중에

4. 해소 (Resolution)
   - 근본 원인 수정
   - 서비스 정상화 확인

5. 사후 검토 (Postmortem)
   - 원인, 영향, 대응 과정 정리
   - 재발 방지 액션 아이템 도출
```

### 런북 (Runbook)

런북은 특정 알림이 발생했을 때 수행해야 할 절차를 문서화한 것이다. 야간에 처음 보는 담당자도 런북을 따라가면 장애를 해결할 수 있어야 한다.

```markdown
# 런북: HighErrorRate - transfer-service

## 증상
transfer-service 5xx 에러율이 1% 초과

## 즉각 확인 사항
1. 최근 배포 여부 확인: kubectl rollout history deployment/transfer-service
2. 에러 로그 확인: kubectl logs -l app=transfer-service --since=5m | grep ERROR
3. 의존성 상태 확인: DB, Redis, Kafka 헬스체크

## 완화 조치
- 최근 배포가 원인이면: kubectl rollout undo deployment/transfer-service
- DB 연결 문제면: DB 연결 풀 설정 확인, RDS 상태 확인
- 외부 의존성 문제면: Circuit Breaker 상태 확인, 수동으로 Open 처리

## 에스컬레이션
- 10분 내 해소 안 되면 팀 리더 호출
- 데이터 손실 의심 시 즉시 CTO 호출
```

### 알림 피로 (Alert Fatigue) 관리

알림이 너무 많으면 담당자가 알림을 끄거나 무시한다. 이를 방지하기 위해:

**알림 분류**: P1(즉각 대응), P2(근무 시간 내 대응), P3(다음 주 처리)로 분류한다.

**알림 억제 (Inhibition)**: 상위 알림이 발생하면 하위 알림을 억제한다. Kafka 브로커가 다운되면 Kafka Consumer 지연 알림은 억제한다.

**알림 그룹핑**: 같은 원인에서 발생하는 여러 알림을 하나로 묶어서 보낸다.

**Dead Man's Switch**: 주기적으로 발생해야 하는 알림이 오지 않을 때 알림을 보내는 역설적 방식이다. 배치 작업이 실행됐는지 확인할 때 유용하다.

### 사후 검토 (Postmortem)

장애 후 팀 전체가 참여해 원인과 개선점을 정리하는 문화다. 중요한 원칙은 "비난 없는 사후 검토(Blameless Postmortem)"다. 개인의 실수가 아니라 시스템과 프로세스의 문제를 찾는다.

```markdown
# Postmortem: 2024-01-15 송금 서비스 장애

## 영향
- 장애 시간: 14:32 ~ 15:17 (45분)
- 영향 사용자: 약 12,000명
- 실패한 송금: 847건 (자동 재처리됨)

## 타임라인
14:32 - DB 커넥션 풀 고갈 시작
14:35 - 에러율 1% 초과, 알림 발생
14:40 - 담당자 확인 시작
14:55 - 원인 파악 (커넥션 누수)
15:10 - 코드 수정 배포
15:17 - 정상화 확인

## 근본 원인
DB 커넥션을 닫지 않는 코드 버그

## 재발 방지
- [ ] 커넥션 누수 감지 메트릭 추가
- [ ] 코드 리뷰 체크리스트에 커넥션 관리 항목 추가
- [ ] 통합 테스트에서 커넥션 풀 고갈 시나리오 추가
```

### 흔한 오해와 함정

**오해: 알림이 많을수록 안전하다**

알림 개수와 시스템 안전성은 비례하지 않는다. 불필요한 알림은 오히려 진짜 장애를 놓치게 만든다. 알림의 품질이 중요하다.

**함정: 완화와 근본 원인 해소를 혼동**

장애 시에는 먼저 완화(서비스 정상화)를 목표로 한다. 롤백이 완화 방법이라면 원인 분석 전에 먼저 롤백한다. 근본 원인 분석과 수정은 서비스가 정상화된 후에 한다.

## PayFlow 연결

PayFlow에서 알림이 필요한 상황은 다음과 같다.

- 송금 실패율 급증
- Outbox 미발행 이벤트 증가
- Kafka Consumer lag 증가
- DLQ 메시지 증가
- 정산 실패
- 지갑 잔액과 원장 대사 불일치

## 실무 포인트

- 증상 알림과 원인 알림을 구분한다.
- 너무 많은 알림은 무시된다.
- 장애 대응 Runbook을 만든다.
- 장애 시작 시각, 영향 범위, 복구 시각을 기록한다.
- 재발 방지 액션을 남긴다.

## 체크 질문

- 좋은 알림의 조건은 무엇인가
- Outbox 미발행 건수가 증가하면 어떤 문제가 생길 수 있는가
- 장애 대응 후 회고가 필요한 이유는 무엇인가

## 실무 설계 보강

### 대표 장애 시나리오

Outbox 미발행 이벤트가 쌓이는데 알림이 없어 원장 누락이 늦게 발견된다.

### 잘못된 구현 예시

~~~text
로그가 있으니 장애가 나면 사람이 알아서 찾을 수 있다고 생각한다.
~~~

### 좋은 구현 예시

~~~text
행동 가능한 알림, 임계치, Runbook, 장애 기록과 후속 조치를 둔다.
~~~

### 대안과 선택 이유

문제가 생기면 로그를 직접 뒤져 해결하는 방식도 있지만, MSA 결제 흐름에서는 원인 추적이 너무 늦어진다. PayFlow는 테스트, 계약 검증, 관측성, SLO, 알림, 장애 주입을 통해 배포 전후에 시스템을 증명하는 방식이 더 전문적이다.

### PayFlow에서 확인할 위치

alert rules, outbox metrics, Kafka lag dashboard, incident docs

### 면접에서 설명하기

좋은 알림은 시끄러운 소리가 아니라 다음 행동을 알려주는 신호다.

### 관련 문서

51, 75, 76

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

Alerting And Incident Response 개념은 PayFlow에서 다음 이유로 중요하다.

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
Alerting And Incident Response 개념은 PayFlow에서 금전 데이터 이상을 운영자가 너무 늦게 발견하지 않게 하기 위해 필요하다.
이 개념이 없으면 Outbox 미발행이 쌓이는데 알림이 없어 원장과 정산 누락이 하루 뒤에야 발견될 수 있다.
그래서 코드에서는 행동 가능한 알림, 임계치, Runbook, 장애 기록과 재발 방지 작업을 두고,
운영에서는 알림 발생/해결 시간, 미확인 알림, 반복 장애를 확인해야 한다.
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
Alerting And Incident Response 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.
