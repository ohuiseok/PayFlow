# 02. Database And Migration

MVP 데이터 모델은 현재 구현할 결제 흐름에 필요한 테이블만 둔다.

## Databases

`payflow_user`

`payflow_wallet`

`payflow_banking`

`payflow_transfer`

`payflow_reward`

`payflow_ledger`

## user-service

### users

사용자 계정과 역할을 저장한다.

필드:

`id`, `email`, `password_hash`, `name`, `role`, `status`, `created_at`, `updated_at`

제약:

`email` unique

`role` in `PARENT`, `CHILD`

`status` in `ACTIVE`, `LOCKED`, `WITHDRAWN`

## wallet-service

### wallets

사용자별 지갑 잔액을 저장한다.

필드:

`id`, `user_id`, `balance`, `currency`, `status`, `version`, `created_at`, `updated_at`

제약:

`user_id` unique

`balance >= 0`

### wallet_transactions

지갑 잔액 변경 이력을 저장한다.

필드:

`id`, `wallet_id`, `type`, `amount`, `balance_after`, `reference_type`, `reference_id`, `idempotency_key`, `created_at`

제약:

`(reference_type, reference_id)` unique

`idempotency_key` unique nullable

`amount > 0`

## banking-service

### bank_accounts

사용자와 연결된 은행 계좌를 저장한다.

필드:

`id`, `user_id`, `bank_code`, `account_number_masked`, `account_holder_name`, `status`, `created_at`, `updated_at`

제약:

`(user_id, bank_code, account_number_masked)` unique

### banking_transfers

은행 충전 요청의 상태를 저장한다.

필드:

`id`, `user_id`, `bank_account_id`, `type`, `amount`, `status`, `idempotency_key`, `request_hash`, `wallet_transaction_id`, `failure_reason`, `created_at`, `updated_at`

제약:

`idempotency_key` unique

`type` in `DEPOSIT`

`status` in `REQUESTED`, `SUCCEEDED`, `FAILED`

## transfer-service

### transfers

사용자 간 송금 요청과 결과를 저장한다.

필드:

`id`, `sender_user_id`, `receiver_user_id`, `amount`, `status`, `idempotency_key`, `request_hash`, `debit_wallet_transaction_id`, `credit_wallet_transaction_id`, `failure_reason`, `created_at`, `updated_at`

제약:

`idempotency_key` unique

`amount > 0`

`sender_user_id != receiver_user_id`

`status` in `REQUESTED`, `PROCESSING`, `SUCCEEDED`, `FAILED`

## reward-service

### parent_child_links

부모와 자녀의 연결 관계를 저장한다.

필드:

`id`, `parent_user_id`, `child_user_id`, `status`, `created_at`, `updated_at`

제약:

`(parent_user_id, child_user_id)` unique

`parent_user_id != child_user_id`

`status` in `ACTIVE`, `INACTIVE`

### reward_tasks

가족 미션과 보상 지급 상태를 저장한다.

필드:

`id`, `parent_user_id`, `child_user_id`, `title`, `description`, `reward_amount`, `status`, `submitted_at`, `approved_at`, `paid_transfer_id`, `created_at`, `updated_at`

제약:

`reward_amount > 0`

`status` in `CREATED`, `SUBMITTED`, `APPROVED`, `PAID`, `REJECTED`, `CANCELED`

## ledger-service

### ledger_entries

하나의 비즈니스 이벤트에 대한 원장 전표를 저장한다.

필드:

`id`, `source_type`, `source_id`, `entry_type`, `occurred_at`, `created_at`

제약:

`(source_type, source_id)` unique

### ledger_lines

전표의 차변/대변 라인을 저장한다.

필드:

`id`, `ledger_entry_id`, `account_code`, `direction`, `amount`, `user_id`, `wallet_id`, `created_at`

제약:

`amount > 0`

`direction` in `DEBIT`, `CREDIT`

전표별 차변 합계와 대변 합계가 같아야 한다.

## Migration Rules

Flyway를 사용한다.

각 서비스는 자기 데이터베이스만 마이그레이션한다.

마이그레이션 파일명은 `V{number}__{description}.sql` 형식을 사용한다.

초기 스키마는 `V1__init.sql`로 시작한다.

테스트 데이터는 운영 마이그레이션에 넣지 않는다.
## PG/Open Banking 확장 테이블

Toss Payments PG 충전과 Open Banking 계좌 연결은 기존 `payflow_banking` DB 안에서 확장한다. 충전 요청은 제공자와 무관한 내부 표준 테이블에 저장하고, Toss/Open Banking 전용 값은 별도 테이블에 분리한다.

### payment_providers

외부 결제 제공자 정보를 저장한다.

필드:

`id`, `provider_code`, `display_name`, `status`, `created_at`, `updated_at`

제약:

`provider_code` unique

`provider_code` in `TOSS_PAYMENTS`, `OPEN_BANKING`

`status` in `ACTIVE`, `DISABLED`

### payment_charges

PG/Open Banking 충전 요청의 내부 표준 상태를 저장한다.

필드:

`id`, `user_id`, `provider_code`, `charge_method`, `amount`, `currency`, `status`, `idempotency_key`, `request_hash`, `wallet_transaction_id`, `failure_code`, `failure_reason`, `created_at`, `updated_at`

제약:

`idempotency_key` unique

`wallet_transaction_id` unique nullable

`amount > 0`

`currency = KRW`

`charge_method` in `TOSS_WIDGET`, `OPEN_BANKING_ACCOUNT`

`status` in `READY`, `PAYMENT_PENDING`, `PAYMENT_APPROVED`, `WALLET_REFLECTING`, `COMPLETED`, `FAILED`, `CANCELED`, `PARTIAL_CANCELED`, `EXPIRED`, `UNKNOWN`, `COMPENSATION_REQUIRED`

### toss_payment_orders

Toss Payments의 주문, 결제키, 승인 결과를 저장한다.

필드:

`id`, `payment_charge_id`, `toss_order_id`, `payment_key`, `order_name`, `method`, `toss_status`, `total_amount`, `balance_amount`, `approved_at`, `receipt_url`, `checkout_url`, `raw_response_json`, `created_at`, `updated_at`

제약:

`payment_charge_id` unique

`toss_order_id` unique

`payment_key` unique nullable

### toss_payment_events

Toss 승인/취소/조회/웹훅 payload를 append-only로 저장한다.

필드:

`id`, `toss_payment_order_id`, `event_type`, `payment_key`, `transaction_key`, `toss_status`, `event_idempotency_key`, `payload_json`, `received_at`

제약:

`event_idempotency_key` unique nullable

`event_type` in `APPROVE_RESPONSE`, `WEBHOOK`, `CANCEL_RESPONSE`, `QUERY_RESPONSE`

### open_banking_authorizations

Open Banking OAuth 연결과 토큰 상태를 저장한다.

필드:

`id`, `user_id`, `state`, `status`, `user_seq_no`, `access_token_encrypted`, `refresh_token_encrypted`, `token_expires_at`, `failure_reason`, `created_at`, `updated_at`

제약:

`state` unique

`status` in `REQUESTED`, `CONNECTED`, `FAILED`, `EXPIRED`, `REVOKED`

### bank_accounts 확장

기존 `bank_accounts`에 다음 필드를 추가한다.

`provider_code`, `open_banking_authorization_id`, `fintech_use_num_encrypted`, `account_alias`, `linked_at`, `last_synced_at`
