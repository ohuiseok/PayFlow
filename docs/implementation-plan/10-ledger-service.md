# 10. Ledger Service

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

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

