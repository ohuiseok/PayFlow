# 14. Testing Strategy

이 문서는 테스트 전략이다.

## 테스트 우선순위

가장 중요한 테스트:

```text
잔액 정합성
멱등성
동시성
Outbox 재발행
원장 중복 방지
보상 중복 지급 방지
장애 상태 기록
```

일반 CRUD 테스트보다 위 항목을 우선한다.

## 테스트 종류

### Unit Test

대상:

```text
도메인 메서드
금액 검증
상태 전이
수수료 계산
request hash
```

보강/2차 대상:

```text
수수료 계산
Redis 분산 락
정산 배치
알림/파일 업로드 URL
```

### Application Service Test

대상:

```text
송금 처리 흐름
보상 미션 승인과 지급 흐름
지갑 잔액 변경
원장 기록
```

보강/2차 대상:

```text
정산 실행
알림 목록/읽음 처리
파일 업로드 URL 발급
```

### Repository Test

대상:

```text
unique constraint
pessimistic lock
상태별 조회
outbox READY 조회
```

### Integration Test

도구:

```text
Testcontainers
MySQL
Kafka
```

초기에는 모든 서비스 통합보다 서비스별 통합 테스트부터 작성한다.
Redis 기반 분산 락 테스트는 보강/2차에서 추가한다.

## 서비스별 필수 테스트

### user-service

```text
회원가입 성공
중복 이메일 실패
로그인 성공
로그인 실패
JWT 발급
```

### wallet-service

```text
지갑 생성
충전
차감
잔액 부족 실패
동시 차감 방지
잔액 변경 이력 저장
```

### transfer-service

```text
송금 성공
Idempotency-Key 없음 실패
같은 key 재호출
같은 key 다른 body 실패
wallet-service 실패 시 FAILED
OutboxEvent 저장
```

### reward-service

```text
부모 미션 등록
아이 완료 요청
부모 승인 시 transfer-service 호출
같은 미션 재승인 시 중복 지급 방지
송금 실패 시 failureReason 저장
missionDate 기반 월별 미션 캘린더 조회
PAID 기준 캐시북 수입 합계 계산
부모/아이 권한 불일치 실패
```

### ledger-service

```text
transfer.completed 이벤트 소비
원장 라인 2개 생성
sourceEventId 중복 skip
원장 조회
```

### settlement-service

보강/2차 테스트다.

```text
정산 후보 저장
수수료 계산
일별 정산
중복 정산 방지
```

## 동시성 테스트 예시

시나리오:

```text
wallet balance = 10000
30 threads
each request transfer 1000
```

기대:

```text
성공 최대 10건
실패 최소 20건
최종 잔액 0 이상
WalletTransaction 성공 건수와 잔액 일치
```

## 멱등성 테스트 예시

시나리오:

```text
동일 Idempotency-Key
동일 request body
100회 반복 요청
```

기대:

```text
Transfer 1건
Wallet 차감 1회
응답은 모두 동일한 transferId
```

## Outbox 테스트 예시

시나리오:

```text
Kafka broker 일시 중단
송금 성공
Outbox READY 저장
Kafka 복구
publisher 재발행
PUBLISHED 변경
```

초기 로컬 테스트에서는 Kafka 중단 자동화가 어려울 수 있으므로 producer mock으로 먼저 검증한다.
