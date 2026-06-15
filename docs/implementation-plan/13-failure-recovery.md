# 13. Failure Recovery

MVP의 장애 복구는 상태값, 멱등키, 거래 이력으로 설명한다.

## Common Rules

외부 호출 전후 상태를 저장한다.

재시도 가능한 요청은 같은 멱등키를 사용한다.

중복 반영 방지는 각 거래 테이블의 unique 제약으로 보장한다.

실패 사유는 `failure_reason`에 남긴다.

## Banking Deposit

은행 처리 실패:

`banking_transfers.status = FAILED`

지갑 입금 실패:

`banking_transfers.status = FAILED`

재시도:

같은 `idempotency_key`와 같은 요청 본문이면 기존 결과를 반환한다.

## Transfer

출금 전 실패:

`transfers.status = FAILED`

출금 후 입금 실패:

`debit_wallet_transaction_id`를 남기고 `FAILED`로 저장한다.

재시도:

같은 `idempotency_key`와 같은 요청 본문이면 기존 결과를 반환한다.

## Reward Payment

보상 지급 전 실패:

미션 상태는 `APPROVED`를 유지한다.

송금 성공 후 상태 저장 실패:

`reward-payment-{missionId}` 멱등키로 송금 결과를 다시 조회하거나 지급 API를 재시도한다.

보상 지급 완료:

`paid_transfer_id`를 저장하고 `PAID`로 변경한다.

## Ledger

원장 기록 실패:

원천 거래의 성공 상태는 유지한다.

같은 `source_type`, `source_id`로 다시 요청하면 중복 전표가 생기지 않는다.

## Tests

충전 은행 실패

충전 지갑 반영 실패

송금 출금 전 실패

송금 출금 후 입금 실패

보상 지급 재시도

원장 기록 중복 요청
