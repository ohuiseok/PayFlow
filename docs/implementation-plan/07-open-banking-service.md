# 07. Banking Service

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

banking-service는 은행 계좌 연결과 지갑 충전을 담당한다.

실제 은행 API 대신 포트폴리오 MVP에서는 명확한 mock client를 사용한다.

## Scope

은행 계좌 연결

연결 계좌 조회

지갑 충전

충전 상태 조회

## APIs

### POST /bank/accounts

요청:

`bankCode`, `accountNumber`, `accountHolderName`

처리:

1. 계좌번호를 마스킹한다.
2. 같은 사용자의 같은 계좌 중복을 확인한다.
3. `bank_accounts`를 생성한다.

### GET /bank/accounts

사용자의 연결 계좌 목록을 반환한다.

### POST /bank/deposits

요청:

`bankAccountId`, `amount`, `idempotencyKey`

처리:

1. 계좌 소유자를 검증한다.
2. 같은 `idempotencyKey` 요청이 있으면 `request_hash`를 비교한다.
3. 기존 요청과 동일하면 저장된 결과를 반환한다.
4. mock 은행 이체를 성공 처리한다.
5. wallet-service에 입금을 요청한다.
6. `banking_transfers`를 `SUCCEEDED`로 저장한다.

### GET /bank/transfers/{bankingTransferId}

충전 요청 상태를 반환한다.

## Status

`REQUESTED`

`SUCCEEDED`

`FAILED`

## Failure Rules

은행 처리 실패는 지갑에 반영하지 않는다.

지갑 입금 실패는 `FAILED`로 기록하고 실패 사유를 남긴다.

같은 멱등키의 다른 요청 본문은 409로 응답한다.

## Tests

계좌 연결 성공

중복 계좌 연결 실패

충전 성공

동일 멱등키 재시도 시 동일 결과 반환

동일 멱등키에 다른 요청 본문이면 409

mock 은행 실패 시 충전 실패

지갑 입금 실패 시 실패 사유 기록

