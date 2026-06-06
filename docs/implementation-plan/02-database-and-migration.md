# 02. Database And Migration

이 문서는 DB 구성과 초기 스키마 설계 기준을 정한다.

## DB 구성

물리 MySQL은 하나를 사용한다.

논리 DB는 서비스별로 분리한다.

```text
payflow_user
payflow_wallet
payflow_banking
payflow_transfer
payflow_reward
payflow_ledger
payflow_settlement
```

서비스는 자기 DB만 접근한다.

## 마이그레이션 전략

초기에는 빠른 구현을 위해 JPA `ddl-auto=update`를 사용할 수 있다.

단, 포트폴리오 완성 단계에서는 Flyway를 도입한다.

권장 최종 설정:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
```

## 초기 구현 단계

초기 구현에서는 아래 순서로 간다.

```text
1. JPA Entity 작성
2. ddl-auto=update로 빠르게 기능 구현
3. 테이블이 안정되면 Flyway V1__init.sql 작성
4. ddl-auto=validate로 변경
```

## DB별 테이블 계획

### payflow_user

```text
users
notification_preferences
```

필드:

```text
id BIGINT PK
email VARCHAR(255) UNIQUE
password VARCHAR(255)
name VARCHAR(100)
role VARCHAR(30)
status VARCHAR(30)
created_at DATETIME
updated_at DATETIME
```

notification_preferences:

```text
id BIGINT PK
user_id BIGINT UNIQUE
mission_submitted BOOLEAN
mission_approved BOOLEAN
mission_rejected BOOLEAN
charge_completed BOOLEAN
created_at DATETIME
updated_at DATETIME
```

### payflow_wallet

```text
wallets
wallet_transactions
```

wallets:

```text
id BIGINT PK
user_id BIGINT UNIQUE
balance DECIMAL(19,0)
status VARCHAR(30)
created_at DATETIME
updated_at DATETIME
```

wallet_transactions:

```text
id BIGINT PK
wallet_id BIGINT
transaction_type VARCHAR(30)
amount DECIMAL(19,0)
balance_after DECIMAL(19,0)
reference_type VARCHAR(30)
reference_id BIGINT
created_at DATETIME
```

주의:

```text
wallet-service는 user DB를 조회하지 않는다.
userId는 외부 서비스에서 받은 참조 ID로만 저장한다.
```

### payflow_banking

```text
bank_accounts
banking_transfers
banking_api_logs
```

bank_accounts:

```text
id BIGINT PK
user_id BIGINT
wallet_id BIGINT
bank_code VARCHAR(10)
account_number_masked VARCHAR(50)
account_holder_name VARCHAR(100)
fintech_use_num VARCHAR(100)
status VARCHAR(30)
created_at DATETIME
updated_at DATETIME
```

banking_transfers:

```text
id BIGINT PK
transfer_type VARCHAR(30)
user_id BIGINT
wallet_id BIGINT
amount DECIMAL(19,0)
status VARCHAR(30)
idempotency_key VARCHAR(255)
request_hash VARCHAR(255)
bank_tran_id VARCHAR(100) UNIQUE
bank_tran_date VARCHAR(8)
tran_dtime VARCHAR(14)
api_tran_id VARCHAR(100)
api_tran_dtm VARCHAR(30)
api_rsp_code VARCHAR(20)
bank_rsp_code VARCHAR(20)
failure_reason VARCHAR(500)
wallet_reference_type VARCHAR(50)
wallet_reference_id VARCHAR(100)
result_check_count INT
next_result_check_at DATETIME
last_result_checked_at DATETIME
requested_at DATETIME
completed_at DATETIME
created_at DATETIME
updated_at DATETIME
```

banking_api_logs:

```text
id BIGINT PK
banking_transfer_id BIGINT
api_name VARCHAR(100)
request_id VARCHAR(100)
http_status INT
api_rsp_code VARCHAR(20)
bank_rsp_code VARCHAR(20)
request_payload_masked TEXT
response_payload_masked TEXT
error_message VARCHAR(500)
created_at DATETIME
```

주의:

```text
banking-service는 wallet DB를 직접 조회하거나 변경하지 않는다.
오픈뱅킹 출금이체 성공이 확정된 뒤 wallet-service 내부 deposit API를 호출한다.
bank_tran_id와 api_tran_id는 외부 은행망 추적과 응답 불명 복구 근거로 저장한다.
충전에서 은행 성공 후 wallet 반영이 실패하면 status는 BANK_SUCCESS_BUT_WALLET_FAILED로 두고, wallet_reference_id = bank_tran_id 기준으로 재처리한다.
계좌번호 원문은 저장하지 않고 마스킹 값 또는 테스트 식별자만 저장한다.
```

### payflow_transfer

```text
transfers
idempotency_keys
outbox_events
```

transfers:

```text
id BIGINT PK
sender_wallet_id BIGINT
receiver_wallet_id BIGINT
amount DECIMAL(19,0)
status VARCHAR(30)
failure_reason VARCHAR(500)
idempotency_key VARCHAR(255)
created_at DATETIME
updated_at DATETIME
```

idempotency_keys:

```text
id BIGINT PK
idempotency_key VARCHAR(255) UNIQUE
request_hash VARCHAR(255)
resource_type VARCHAR(50)
resource_id BIGINT
status VARCHAR(30)
response_body TEXT
created_at DATETIME
updated_at DATETIME
```

outbox_events:

```text
id BIGINT PK
event_id VARCHAR(100) UNIQUE
aggregate_type VARCHAR(50)
aggregate_id BIGINT
event_type VARCHAR(100)
payload JSON 또는 TEXT
status VARCHAR(30)
retry_count INT
last_error TEXT
created_at DATETIME
published_at DATETIME
updated_at DATETIME
```

### payflow_reward

```text
families
family_invitations
family_link_requests
reward_tasks
reward_task_submissions
cashbook_entries
notifications
file_upload_requests
```

families:

```text
id BIGINT PK
parent_user_id BIGINT
child_user_id BIGINT
status VARCHAR(30)
connected_at DATETIME
disconnected_at DATETIME
created_at DATETIME
updated_at DATETIME
```

family_invitations:

```text
id BIGINT PK
parent_user_id BIGINT
invite_code VARCHAR(30) UNIQUE
status VARCHAR(30)
expires_at DATETIME
created_at DATETIME
updated_at DATETIME
```

family_link_requests:

```text
id BIGINT PK
invitation_id BIGINT
parent_user_id BIGINT
child_user_id BIGINT
status VARCHAR(30)
reject_reason VARCHAR(500)
created_at DATETIME
updated_at DATETIME
```

reward_tasks:

```text
id BIGINT PK
parent_user_id BIGINT
child_user_id BIGINT
parent_wallet_id BIGINT
child_wallet_id BIGINT
title VARCHAR(100)
description VARCHAR(500)
reward_amount DECIMAL(19,0)
mission_date DATE
evidence_required BOOLEAN
status VARCHAR(30)
rejection_reason VARCHAR(500)
submitted_at DATETIME
approved_at DATETIME
paid_at DATETIME
transfer_id BIGINT
failure_reason VARCHAR(500)
created_at DATETIME
updated_at DATETIME
```

reward_task_submissions:

```text
id BIGINT PK
reward_task_id BIGINT
submitter_user_id BIGINT
memo VARCHAR(1000)
evidence_image_url VARCHAR(1000)
created_at DATETIME
```

cashbook_entries:

```text
id BIGINT PK
child_user_id BIGINT
wallet_id BIGINT
mission_id BIGINT NULL
title VARCHAR(100)
description VARCHAR(500)
amount DECIMAL(19,0)
entry_type VARCHAR(30)
created_at DATETIME
```

notifications:

```text
id BIGINT PK
user_id BIGINT
title VARCHAR(100)
body VARCHAR(500)
notification_type VARCHAR(50)
read_at DATETIME NULL
created_at DATETIME
```

file_upload_requests:

```text
id BIGINT PK
mission_id BIGINT
user_id BIGINT
file_name VARCHAR(255)
content_type VARCHAR(100)
file_url VARCHAR(1000)
expires_at DATETIME
created_at DATETIME
```

주의:

```text
reward-service는 user, wallet, transfer DB를 직접 조회하거나 변경하지 않는다.
부모/아이 식별자와 지갑 ID는 참조 ID로만 저장한다.
보상 지급은 transfer-service 송금 API를 통해서만 수행한다.
MVP에서는 가족 연결, 미션, 캐시북을 reward DB에 둔다.
알림과 인증 사진 업로드 URL은 보강/2차에서 reward DB에 함께 둘 수 있다.
트래픽이나 책임이 커지면 notification-service/file-service/family-service로 분리한다.
```

### payflow_ledger

```text
ledger_entries
ledger_lines
processed_events
```

ledger_entries:

```text
id BIGINT PK
transfer_id BIGINT UNIQUE
source_event_id VARCHAR(100) UNIQUE
entry_type VARCHAR(50)
total_amount DECIMAL(19,0)
created_at DATETIME
```

ledger_lines:

```text
id BIGINT PK
ledger_entry_id BIGINT
wallet_id BIGINT
direction VARCHAR(10)
amount DECIMAL(19,0)
created_at DATETIME
```

direction:

```text
DEBIT
CREDIT
```

processed_events:

```text
id BIGINT PK
source_event_id VARCHAR(100) UNIQUE
consumer_name VARCHAR(100)
processed_at DATETIME
```

### payflow_settlement

```text
settlement_days
settlement_items
```

settlement_days:

```text
id BIGINT PK
settlement_date DATE UNIQUE
total_transfer_amount DECIMAL(19,0)
total_fee_amount DECIMAL(19,0)
status VARCHAR(30)
created_at DATETIME
updated_at DATETIME
```

settlement_items:

```text
id BIGINT PK
settlement_day_id BIGINT
transfer_id BIGINT
amount DECIMAL(19,0)
fee_amount DECIMAL(19,0)
created_at DATETIME
```

## 인덱스

반드시 필요한 인덱스:

```text
users.email UNIQUE
wallets.user_id UNIQUE
wallet_transactions.wallet_id
wallet_transactions(wallet_id, transaction_type, reference_type, reference_id) UNIQUE
bank_accounts.user_id
bank_accounts.wallet_id
bank_accounts.fintech_use_num
banking_transfers.idempotency_key UNIQUE
banking_transfers.bank_tran_id UNIQUE
banking_transfers.status
banking_transfers.wallet_reference_id UNIQUE
transfers.idempotency_key UNIQUE
transfers.status
idempotency_keys.idempotency_key UNIQUE
outbox_events.status
outbox_events(status, created_at)
outbox_events.event_id UNIQUE
reward_tasks(child_user_id, mission_date)
reward_tasks(parent_user_id, mission_date)
reward_tasks.status
ledger_entries.transfer_id UNIQUE
ledger_entries.source_event_id UNIQUE
processed_events.source_event_id UNIQUE
settlement_days.settlement_date UNIQUE
settlement_items.transfer_id UNIQUE
```

## DB 직접 참조 금지

금지 예:

```text
transfer-service가 payflow_wallet.wallets 직접 조회
banking-service가 payflow_wallet.wallets 직접 수정
reward-service가 payflow_transfer.transfers 직접 수정
ledger-service가 payflow_transfer.transfers 직접 조회
settlement-service가 payflow_wallet.wallet_transactions 직접 조회
```

허용:

```text
OpenFeign 호출
Kafka 이벤트 소비
자기 DB 조회
```
