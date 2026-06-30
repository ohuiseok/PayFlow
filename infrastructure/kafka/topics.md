# Kafka Topics

Current Kafka topics used by the application.

| Topic | Producer | Consumer |
|---|---|---|
| `transfer.completed` | transfer-service outbox publisher | ledger-service |
| `transfer.failed` | transfer-service outbox publisher | ledger-service |
| `payment.settlement` | banking-service payment settlement outbox relay | settlement-service |

`settlement-service` does not consume `transfer.completed` or `ledger.recorded`. During its daily batch it queries the ledger-service payment entry API synchronously for each collected Toss `CHARGE`/`CANCEL` event.
