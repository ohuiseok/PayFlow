# 13. Failure Recovery

이 문서는 장애와 복구 시나리오를 정의한다.

결제 포트폴리오는 정상 동작보다 장애 상황 설명이 더 중요하다.

## 주요 장애 시나리오

### 1. 동일 요청 반복

상황:

```text
사용자가 송금 버튼을 여러 번 누름
네트워크 재시도로 같은 요청이 반복됨
```

대응:

```text
Idempotency-Key로 한 번만 처리
완료된 요청은 기존 응답 반환
```

### 2. 같은 지갑 동시 송금

상황:

```text
잔액 10,000원 지갑에서 동시에 10,000원 송금 2건
```

대응:

```text
Redis lock
wallet-service DB row lock
잔액 부족 실패
```

검증:

```text
성공 1건
실패 1건
잔액 음수 없음
```

### 3. wallet-service 장애

상황:

```text
transfer-service가 wallet-service 호출 실패
```

대응:

```text
CircuitBreaker
Timeout
Transfer FAILED
failureReason 저장
```

### 4. sender 차감 성공, receiver 증가 실패

상황:

```text
송신자 지갑 차감 성공
수신자 지갑 증가 실패
```

1차 대응:

```text
Transfer COMPENSATION_REQUIRED
failureReason 저장
운영자 확인 필요 상태로 둠
```

2차 대응:

```text
보상 입금 호출
성공 시 ROLLED_BACK
실패 시 ROLLBACK_FAILED
```

초기 구현에서는 1차 대응을 먼저 구현한다.

### 5. DB 저장 성공, Kafka 발행 실패

대응:

```text
Transactional Outbox Pattern
OutboxEvent READY 상태 유지
Publisher 재시도
```

### 6. Kafka 중복 이벤트

대응:

```text
consumer별 processed_events 테이블
eventId unique
이미 처리한 이벤트 skip
```

### 6-1. Kafka consumer 반복 실패와 DLQ

상황:

```text
transfer.completed 이벤트는 발행됐지만 ledger-service consumer가 계속 실패
consumer 재시도 후에도 원장 기록 불가
```

대응:

```text
consumer retry 한도 적용
한도 초과 시 *.dlq topic으로 원본 이벤트와 실패 원인 발행
DLQ payload에 eventId, originalTopic, consumerGroup, attempts, failureReason 저장
운영자 또는 재처리 API가 DLQ 이벤트를 기준으로 원인 확인 후 재처리
```

검증:

```text
일시 실패는 retry 후 성공
반복 실패는 DLQ에 남음
DLQ로 이동해도 원본 eventId 기준 consumer 멱등성은 유지
```

### 7. transfer-service 재시작

상황:

```text
PROCESSING 상태로 남은 송금
READY 상태의 outbox event
```

대응:

```text
PROCESSING 상태 점검 배치 또는 관리 API
READY outbox event 재발행
```

초기 구현:

```text
READY outbox 재발행까지만 구현
PROCESSING 복구는 5분 이상 stale 기준으로 문서화하고, 자동 복구는 후순위
```

### 8. reward-service 승인 재시도

상황:

```text
부모가 승인 버튼을 여러 번 누름
reward-service가 transfer-service 호출 후 응답을 받기 전에 timeout
PAYMENT_PENDING 상태에서 사용자가 다시 승인 요청
```

대응:

```text
reward-service는 이미 PAID인 미션을 다시 지급하지 않는다.
transfer-service 호출에는 reward-payment-mission-{missionId} Idempotency-Key를 사용한다.
PAYMENT_PENDING 재시도도 같은 Idempotency-Key를 사용한다.
transferId와 paidAt을 저장해 캐시북 수입 기록, 미션 캘린더 상태, 지급 결과를 연결한다.
```

## Resilience4j 설정 기준

초기값:

```text
failure-rate-threshold: 50
minimum-number-of-calls: 5
wait-duration-in-open-state: 5s
sliding-window-size: 10
timeout-duration: 3s
retry max-attempts: 2 or 3
```

주의:

```text
변경 요청에 무조건 retry를 걸면 중복 처리 위험이 있다.
wallet-service API는 referenceId 기반 멱등성을 가져야 retry가 안전하다.
```

## 테스트 시나리오

반드시 자동화할 테스트:

```text
동일 Idempotency-Key 100회 요청 -> 차감 1회
같은 지갑 동시 송금 30건 -> 잔액 음수 없음
wallet-service down -> Transfer FAILED
sender 차감 성공 후 receiver 증가 실패 -> COMPENSATION_REQUIRED
Outbox publisher 실패 -> READY 유지 또는 retryCount 증가
Kafka 중복 이벤트 -> ledger 1회만 기록
Kafka consumer 반복 실패 -> DLQ에 원본 이벤트와 실패 원인 저장
reward-service 승인 재시도 -> 아이 지갑 지급 1회
```
