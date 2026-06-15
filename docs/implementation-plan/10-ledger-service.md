# 10. Ledger Service

ledger-service는 결제 이벤트를 복식부기 원장으로 기록한다.

## Scope

송금 원장 기록

보상 지급 원장 기록

원장 조회

전표 차대 일치 검증

## APIs

### POST /internal/ledger/entries

요청:

`sourceType`, `sourceId`, `entryType`, `occurredAt`, `lines`

처리:

1. `sourceType`, `sourceId` 중복을 확인한다.
2. 차변 합계와 대변 합계를 검증한다.
3. `ledger_entries`를 생성한다.
4. `ledger_lines`를 생성한다.

### GET /ledger/entries

사용자 기준 원장 내역을 조회한다.

### GET /ledger/entries/{entryId}

전표와 라인 상세를 조회한다.

## Account Codes

`USER_WALLET`

`SYSTEM_CLEARING`

`REWARD_EXPENSE`

## Entry Types

`TRANSFER`

`REWARD_PAYMENT`

`BANKING_DEPOSIT`

## Rules

같은 source는 한 번만 기록한다.

차변 합계와 대변 합계가 다르면 저장하지 않는다.

금액은 양수만 허용한다.

원장 데이터는 수정하지 않는다.

## Tests

송금 원장 기록 성공

보상 지급 원장 기록 성공

같은 source 재요청 시 기존 전표 반환

차대 불일치 저장 실패

사용자 원장 조회 성공
