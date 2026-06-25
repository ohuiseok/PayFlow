# 04. Wallet Service

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

wallet-service는 지갑 잔액과 잔액 변경 이력을 담당한다.

## Scope

사용자 지갑 생성

내 지갑 조회

입금

출금

거래 이력 조회

## APIs

### POST /internal/wallets

회원 가입 후 사용자 지갑을 생성한다.

요청:

`userId`

### GET /wallets/me

인증된 사용자의 지갑과 잔액을 반환한다.

### POST /internal/wallets/{userId}/credit

지갑에 금액을 입금한다.

요청:

`amount`, `referenceType`, `referenceId`, `idempotencyKey`

### POST /internal/wallets/{userId}/debit

지갑에서 금액을 출금한다.

요청:

`amount`, `referenceType`, `referenceId`, `idempotencyKey`

### GET /wallets/me/transactions

인증된 사용자의 지갑 거래 이력을 반환한다.

## Rules

지갑은 사용자당 하나다.

출금 후 잔액은 0 이상이어야 한다.

`reference_type`, `reference_id` 조합은 중복될 수 없다.

동일 reference 요청은 기존 거래 결과를 반환한다.

입금/출금은 하나의 DB transaction에서 잔액과 거래 이력을 함께 저장한다.

## Transaction Types

`CREDIT`

`DEBIT`

## Reference Types

`BANKING_DEPOSIT`

`TRANSFER_DEBIT`

`TRANSFER_CREDIT`

`REWARD_PAYMENT`

## Tests

지갑 생성 성공

중복 지갑 생성 시 기존 지갑 반환

입금 성공

출금 성공

잔액 부족 출금 실패

동일 reference 재시도 시 중복 반영 없음

동시 출금 시 잔액 음수 방지

