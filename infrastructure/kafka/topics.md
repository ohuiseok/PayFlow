# Kafka Topics

Initial topic plan for the payment flow.

| Topic | Producer | Consumer |
|---|---|---|
| `transfer.completed` | transfer-service outbox publisher | ledger-service, settlement-service |
| `transfer.failed` | transfer-service outbox publisher | ledger-service |
| `ledger.recorded` | ledger-service | settlement-service |
