# 05. Transfer Service

transfer-service는 사용자 간 송금을 처리한다.

멱등성은 `transfers.idempotency_key`와 `request_hash`로 처리한다.

## Scope

송금 요청 생성

송금 상태 조회

지갑 출금/입금 호출

원장 기록 요청

## APIs

### POST /transfers

요청:

`receiverUserId`, `amount`, `idempotencyKey`

처리:

1. 송금자와 수신자가 다른지 검증한다.
2. 같은 `idempotencyKey` 요청이 있으면 `request_hash`를 비교한다.
3. 기존 요청과 동일하면 저장된 결과를 반환한다.
4. 요청 내용이 다르면 409를 반환한다.
5. `transfers`를 `REQUESTED`로 생성한다.
6. 송금자 지갑에서 출금한다.
7. 수신자 지갑에 입금한다.
8. 상태를 `SUCCEEDED`로 변경한다.
9. ledger-service에 원장 기록을 요청한다.

### GET /transfers/{transferId}

송금 상태와 거래 정보를 반환한다.

### GET /transfers

내 송금 목록을 반환한다.

## Status

`REQUESTED`

`PROCESSING`

`SUCCEEDED`

`FAILED`

## Failure Rules

출금 전 실패는 `FAILED`로 종료한다.

출금 후 입금 실패는 `FAILED`로 저장하고 `debit_wallet_transaction_id`를 남긴다.

원장 기록 실패는 송금 성공 상태를 유지하고 실패 사유를 로그로 남긴다.

운영 복구는 `transfers`와 `wallet_transactions`의 reference를 기준으로 판단한다.

## Tests

송금 성공

잔액 부족 실패

동일 멱등키 재시도 시 동일 결과 반환

동일 멱등키에 다른 요청 본문이면 409

동시 송금 시 잔액이 음수가 되지 않음

출금 후 입금 실패 상태 기록

송금 성공 후 원장 기록 요청
