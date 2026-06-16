# Next Technical Notes

## Redis Distributed Lock

Keep Redis distributed lock as a remaining task for transfer concurrency.

Target area:

- `transfer-service`
- Sender wallet money movement in `TransferService.processNewTransfer`

Recommended lock shape:

- Lock key: `transfer:wallet-lock:{senderWalletId}`
- Value: random owner token
- TTL: short, for example 3-5 seconds
- Unlock: compare token before deleting
- Scope: acquire before wallet withdraw/deposit sequence, release in `finally`

Reason:

- Current wallet-service already uses DB row locking for balance mutation.
- Transfer-service still needs a service-level guard to prevent duplicated concurrent transfer processing around idempotency and cross-service calls.

## Kafka MSA Status

Current status: transfer-to-ledger is Kafka-driven.

Already present:

- Kafka container in `docker-compose.yml`
- Kafka config/dependencies in `transfer-service`, `ledger-service`, and `settlement-service`
- Topic plan in `infrastructure/kafka/topics.md`
- `transfer-service` publishes `transfer.completed` and `transfer.failed`
- `ledger-service` consumes `transfer.completed`

Not implemented yet:

- Transactional outbox table/publisher
- Kafka-based wallet money movement saga
- `transfer.failed` consumer/recovery flow

Current transfer flow:

- API Gateway routes HTTP requests to services.
- `transfer-service` calls `wallet-service` through OpenFeign HTTP clients for wallet debit/credit.
- `transfer-service` publishes a Kafka event after successful wallet movement.
- `ledger-service` records double-entry ledger rows from the Kafka event.

So the architecture is now mixed: synchronous HTTP for wallet money movement and Kafka event flow for ledger recording.
