# Next Technical Notes

## Current Kafka MSA Status

Current status: transfer-to-ledger is Kafka-driven and protected by transactional outbox.

Already implemented:

- Kafka container in `docker-compose.yml`
- Kafka config/dependencies in `transfer-service`, `ledger-service`, and `settlement-service`
- Topic plan in `infrastructure/kafka/topics.md`
- `transfer-service` stores `transfer.completed` and `transfer.failed` in `outbox_events`
- Outbox relay claims events with `PROCESSING`, publishes to Kafka, marks `PUBLISHED`, and retries `FAILED`
- Outbox relay recovers stale `PROCESSING` events after `processing-timeout`
- `ledger-service` consumes `transfer.completed` idempotently by `transferId`
- `ledger-service` consumes `transfer.failed` idempotently by `transferId`
- `ledger-service` exposes failure tracking APIs:
  - `GET /api/ledgers/transfer-failures`
  - `GET /api/ledgers/transfer-failures/{transferId}`
- `transfer-service` exposes outbox publishing summary API:
  - `GET /api/transfers/outbox/summary`

Current transfer flow:

- API Gateway routes HTTP requests to services.
- `transfer-service` calls `wallet-service` through OpenFeign HTTP clients for wallet debit/credit.
- Sender wallet money movement is guarded with Redis lock key `transfer:wallet-lock:{senderWalletId}`.
- `transfer-service` writes transfer state and outbox event intent in the same database transaction.
- Outbox relay publishes Kafka events.
- `ledger-service` records double-entry ledger rows from `transfer.completed`.
- `ledger-service` records failed transfer tracking rows from `transfer.failed`.

The architecture is still mixed: synchronous HTTP for wallet money movement and Kafka event flow for ledger recording/failure tracking.

## Remaining Technical Work

Recommended next tasks:

- Add metrics/alerts for outbox lag, retry count, stuck recovery count, and publish failure count.
- Add recovery workflow for `COMPENSATION_REQUIRED` transfers.
- Add DLQ strategy for events that exceed outbox max retries.
- Add integration tests with real Kafka/Redis via Testcontainers.
- Decide whether wallet money movement should remain synchronous HTTP or evolve into a Kafka-based saga.
