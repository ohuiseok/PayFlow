# Next Technical Notes

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

## Frontend/API Integration Status

Current status: `sample-react` is aligned with the current backend MVP API surface.

Updated integration points:

- Banking screens now use `GET /api/bank/accounts`, `POST /api/bank/accounts`, `POST /api/bank/deposits`, and `GET /api/bank/transfers/{bankingTransferId}`.
- Mission screens now use array responses from `GET /api/missions` and PATCH actions for submit/approve/reject.
- Agency approval calls both `PATCH /api/missions/{missionId}/approve` and `POST /api/missions/{missionId}/pay` so approval actually triggers reward payment.
- Family linking now matches the current reward-service contract: a parent connects directly with `POST /api/families/links` using `childUserId`.
- Agency credit summary reads `GET /api/cashbook/parent/summary`.
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

- Youth-side family invitation remains a UI concept; the backend currently only supports parent-created direct links.

## Current Kafka MSA Status

Current status: transfer-to-ledger와 Toss-to-settlement 흐름이 Kafka 기반이며 각각 transactional outbox를 사용한다.

Already implemented:

- Kafka container in `docker-compose.yml`
- Kafka config/dependencies in `banking-service`, `transfer-service`, `ledger-service`, and `settlement-service`
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
- `banking-service` stores Toss `CHARGE`/`CANCEL` events in `payment_settlement_outbox`
- banking outbox relay publishes `payment.settlement` and retries failed rows up to the configured limit
- `settlement-service` consumes `payment.settlement` idempotently by `eventId`
- the daily Spring Batch job reconciles each event with `ledger-service` and stores `MATCHED`, `MISSING_LEDGER`, or `AMOUNT_MISMATCH`
- settlement runs at 01:00 Asia/Seoul for the previous day and is also available through `/api/settlements/daily/{businessDate}`

Current transfer flow:

- API Gateway routes HTTP requests to services.
- `transfer-service` calls `wallet-service` through OpenFeign HTTP clients for wallet debit/credit.
- Sender wallet money movement is guarded with Redis lock key `transfer:wallet-lock:{senderWalletId}`.
- `transfer-service` writes transfer state and outbox event intent in the same database transaction.
- Outbox relay publishes Kafka events.
- `ledger-service` records double-entry ledger rows from `transfer.completed`.
- `ledger-service` records failed transfer tracking rows from `transfer.failed`.
- `transfer-service` exposes compensation lookup/refund APIs for `COMPENSATION_REQUIRED` transfers.

The architecture is mixed by design: synchronous HTTP for wallet money movement and ledger lookup, Kafka for transfer/settlement event delivery.

## Remaining Technical Work

Recommended next tasks:

- Add metrics/alerts for outbox lag, retry count, stuck recovery count, and publish failure count.
- Add automated/retry workflow for compensation refunds if manual refund API is not enough.
- Add DLQ strategy for events that exceed outbox max retries.
- Add integration tests with real Kafka/Redis via Testcontainers.
- Add DLT/error handling for the settlement consumer and monitoring for `payment_settlement_outbox` lag/retry exhaustion.
- Restrict manual settlement execution to an operator/admin role.
- Move settlement schema management from Hibernate update and ad-hoc SQL initialization to one migration strategy.
- Decide whether wallet money movement should remain synchronous HTTP or evolve into a Kafka-based saga.

