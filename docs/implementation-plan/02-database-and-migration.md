# 02. Database And Migration

이 문서는 DB 구성과 초기 스키마 설계 기준을 정한다.

## DB 구성

물리 MySQL은 하나를 사용한다.

논리 DB는 서비스별로 분리한다.

```text
payflow_user
payflow_wallet
payflow_transfer
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
```

필드:

```text
id BIGINT PK
email VARCHAR(255) UNIQUE
password VARCHAR(255)
name VARCHAR(100)
status VARCHAR(30)
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
event_id VARCHAR(100) UNIQUE
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
event_id VARCHAR(100) UNIQUE
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
transfers.idempotency_key UNIQUE
transfers.status
idempotency_keys.idempotency_key UNIQUE
outbox_events.status
outbox_events(status, created_at)
outbox_events.event_id UNIQUE
ledger_entries.transfer_id UNIQUE
ledger_entries.event_id UNIQUE
processed_events.event_id UNIQUE
settlement_days.settlement_date UNIQUE
settlement_items.transfer_id UNIQUE
```

## DB 직접 참조 금지

금지 예:

```text
transfer-service가 payflow_wallet.wallets 직접 조회
ledger-service가 payflow_transfer.transfers 직접 조회
settlement-service가 payflow_wallet.wallet_transactions 직접 조회
```

허용:

```text
OpenFeign 호출
Kafka 이벤트 소비
자기 DB 조회
```
