# Next Technical Notes

## Frontend/API Integration Status

Current status: `sample-react` is aligned with the current backend MVP API surface.

Updated integration points:

- Banking screens now use `GET /api/bank/accounts`, `POST /api/bank/accounts`, `POST /api/bank/deposits`, and `GET /api/bank/transfers/{bankingTransferId}`.
- Mission screens now use array responses from `GET /api/missions` and PATCH actions for submit/approve/reject.
- Parent approval calls both `PATCH /api/missions/{missionId}/approve` and `POST /api/missions/{missionId}/pay` so approval actually triggers reward payment.
- Family linking now matches the current reward-service contract: a parent connects directly with `POST /api/families/links` using `childUserId`.
- Parent credit summary reads `GET /api/cashbook/parent/summary`.
- Cashbook entries now read the backend `MissionResponse[]` shape from `GET /api/cashbook/children/{childUserId}/entries`.
- Frontend verification scripts now include `npm run typecheck` and `npm test` aliases.
- `tsconfig.json` excludes generated output (`dist`, `.expo`, `node_modules`) so type checks do not race against web export.

Verified on 2026-06-18:

- `sample-react`: `npm run check`
- `docker compose config --quiet`
- `banking-service`: `gradlew test`
- `reward-service`: `gradlew test`
- `transfer-service`: `gradlew test`
- `ledger-service`: `gradlew test`

Not yet verified:

- Full Docker Compose runtime smoke test. Docker Desktop was not running in the local environment.

Remaining frontend/API gaps:

- Child-side family invitation remains a UI concept; the backend currently only supports parent-created direct links.

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
- `transfer-service` exposes compensation lookup/refund APIs for `COMPENSATION_REQUIRED` transfers.

The architecture is still mixed: synchronous HTTP for wallet money movement and Kafka event flow for ledger recording/failure tracking.

## Remaining Technical Work

Recommended next tasks:

- Add metrics/alerts for outbox lag, retry count, stuck recovery count, and publish failure count.
- Add automated/retry workflow for compensation refunds if manual refund API is not enough.
- Add DLQ strategy for events that exceed outbox max retries.
- Add integration tests with real Kafka/Redis via Testcontainers.
- Decide whether wallet money movement should remain synchronous HTTP or evolve into a Kafka-based saga.
