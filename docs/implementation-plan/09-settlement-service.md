# 09. Settlement Service

## 목적

Toss PG 승인·취소 거래를 Kafka로 수집하고, 기준일별 금액을 집계한 뒤 원장 기록과 대사한다.

## 입력 흐름

1. `banking-service`가 Toss 승인 또는 취소를 처리한다.
2. 같은 DB 트랜잭션에서 `payment_settlement_outbox`에 이벤트를 저장한다.
3. relay가 `payment.settlement` 토픽으로 발행한다.
4. `settlement-service`가 `event_id` 기준으로 멱등하게 `settlement_transactions`에 저장한다.

이벤트의 주요 값은 `eventId`, `type(CHARGE/CANCEL)`, `chargeId`, `userId`, `paymentKey`, `amount`, `currency`, `ledgerSourceType`, `occurredAt`이다.

## 일별 배치

- Job: `dailyPaymentSettlementJob`
- 기본 실행: 매일 `01:00`, `Asia/Seoul`, 전일 기준
- 수동 API: `POST /api/settlements/daily/{businessDate}`
- 조회 API: `GET /api/settlements/daily/{businessDate}`
- Reader: `[businessDate 00:00, 다음 날 00:00)` 범위, ID 오름차순, page/chunk 100
- Processor: `ledger-service`의 `/ledgers/internal/payment-entry` 조회
- Writer: 거래별 원장 일치 여부를 `settlement_items`에 저장

## 집계와 상태

```text
grossAmount       = CHARGE 합계
cancelAmount      = CANCEL 합계
feeAmount         = grossAmount × feeRate, 원 단위 HALF_UP
expectedNetAmount = grossAmount - cancelAmount - feeAmount
```

기본 수수료율은 `0.027`이며 `SETTLEMENT_FEE_RATE`로 변경한다.

- 실행 상태: `RUNNING`, `COMPLETED`, `WITH_DISCREPANCY`, `FAILED`
- 대사 상태: `MATCHED`, `MISSING_LEDGER`, `AMOUNT_MISMATCH`
- 완료 또는 차이 있음 상태의 기준일을 다시 호출하면 기존 결과를 반환한다.

## 데이터

- `settlement_transactions`: Kafka 원천 이벤트
- `settlement_runs`: 기준일별 실행과 집계 결과
- `settlement_items`: 거래별 원장 대사 결과
- Spring Batch 메타데이터 테이블: job/step 실행 상태

## 현재 한계

- 수수료 정책 버전, 최소/최대 수수료, 적용 시작일은 아직 모델링하지 않는다.
- 별도의 정산 대상 스냅샷 테이블이나 운영자 승인/확정 단계는 없다.
- 원장 조회는 거래마다 동기 HTTP로 호출하므로 대량 처리 시 bulk 조회가 필요하다.
- Kafka consumer DLT와 정산 outbox 모니터링 API가 없다.
- 정산 API에는 관리자 역할 검증이 없고 인증된 사용자가 호출할 수 있어 운영 API 권한 분리가 필요하다.
- settlement 스키마는 현재 Hibernate `ddl-auto=update`와 `batch-schema.sql`에 의존한다. 운영 전 Flyway 단일화가 필요하다.

## 테스트

`DailyPaymentSettlementJobTest`에서 다음을 검증한다.

- 같은 이벤트를 두 번 소비해도 한 건만 저장
- 승인/취소 합계와 2.7% 수수료 계산
- 원장 조회 결과와 금액 대사
- 완료된 기준일 재호출 시 기존 실행 결과 반환
