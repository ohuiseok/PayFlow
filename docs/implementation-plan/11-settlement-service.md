# 11. Settlement Service

`settlement-service`는 정산과 배치를 보여주는 확장 서비스다.

Docker Compose에서는 profile로 필요할 때만 실행한다.

## 목표

구현할 기능:

```text
일별 거래 집계
수수료 계산
정산 결과 저장
정산 재실행 방지
Spring Batch 기반 chunk 처리
```

## 초기 정산 정책

수수료 정책은 단순하게 시작한다.

```text
송금 금액의 1%
최소 수수료 0원
최대 수수료 제한 없음
원 단위 절사
```

예:

```text
10,000원 송금 -> 수수료 100원
```

추후 정책:

```text
구간별 수수료
무료 송금 횟수
사용자 등급별 수수료
```

## 데이터 소스

초기에는 아래 중 하나를 선택한다.

권장 1차:

```text
ledger.recorded 이벤트를 settlement-service DB에 settlement_items 후보로 저장
```

대안:

```text
transfer.completed 이벤트를 직접 소비
```

초기 구현은 단순하게 `transfer.completed` 이벤트를 소비해 정산 후보를 저장한다.

## 엔티티

### SettlementTarget

```text
id
transferId
amount
feeAmount
transferCompletedAt
status
createdAt
```

### SettlementDay

```text
id
settlementDate
totalTransferAmount
totalFeeAmount
status
createdAt
updatedAt
```

### SettlementItem

```text
id
settlementDayId
transferId
amount
feeAmount
createdAt
```

## Batch Job

Job name:

```text
dailySettlementJob
```

Step:

```text
1. settlement target reader
2. fee/aggregation processor
3. settlement item writer
4. settlement day summary writer
```

초기에는 단일 step으로 단순 구현 가능.

## API

### 정산 실행

```http
POST /settlements/daily?date=2026-05-30
```

응답:

```json
{
  "settlementDate": "2026-05-30",
  "status": "COMPLETED",
  "totalTransferAmount": 1000000,
  "totalFeeAmount": 10000
}
```

### 정산 조회

```http
GET /settlements/daily/2026-05-30
```

## 구현 순서

1. Spring Batch 설정 확인
2. SettlementTarget 엔티티 작성
3. SettlementDay 엔티티 작성
4. SettlementItem 엔티티 작성
5. TransferCompletedEvent consumer 작성
6. 수수료 계산기 작성
7. dailySettlementJob 작성
8. 정산 실행 API 작성
9. 테스트 작성

## 테스트

보강/2차 필수 테스트:

```text
transfer.completed 이벤트 수신 시 정산 후보 저장
같은 transferId 중복 저장 방지
수수료 계산 검증
일별 정산 실행
이미 정산된 날짜 재실행 방지
정산 결과 조회
```
