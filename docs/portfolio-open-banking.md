# Portfolio Note: Open Banking Integration

This note summarizes the PayFlow Open Banking integration from a portfolio and interview perspective.
The core point is not "I called an external API", but "I modeled financial uncertainty, idempotency, and sensitive data handling explicitly."

## Problem

Open Banking transfer APIs do not behave like ordinary CRUD APIs.
HTTP 200 only means the API server returned a response; it does not always mean the bank transfer is final.

PayFlow handles these cases separately:

- final success
- explicit failure
- processing
- timeout or ambiguous response
- duplicate transaction id

## Implemented Flow

### Account Connection

```text
GET  /bank/openbanking/authorize-url
POST /bank/openbanking/callback
POST /bank/openbanking/accounts/sync
```

Callback flow:

```text
authorization code
-> /oauth/2.0/token
-> encrypted token save
-> /v2.0/user/me
-> bank_accounts sync
```

The service stores account metadata such as `fintechUseNum`, `userSeqNo`, bank code, bank name, masked account number, holder name, and agreement flags.
It does not store raw authorization codes, raw tokens, or raw account numbers.

### Wallet Charge

PayFlow charge maps to Open Banking withdraw transfer.

```text
POST /bank/deposits
-> /v2.0/transfer/withdraw/fin_num
-> bank success confirmed
-> wallet-service deposit
```

State flow:

```text
REQUESTED
-> BANK_PROCESSING
-> BANK_SUCCEEDED
-> WALLET_REFLECTING
-> COMPLETED
```

Wallet money is reflected only after bank success is confirmed.

### Result Check

Ambiguous transactions are not marked as failed immediately.
They remain retryable and are finalized through transfer result checks.

```text
POST /bank/transfers/{bankingTransferId}/result-check
OpenBankingResultCheckScheduler
```

The scheduler checks `BANK_PROCESSING` and `UNKNOWN` transfers when `nextResultCheckAt <= now`.

## Idempotency Strategy

| Layer | Key | Purpose |
|---|---|---|
| API request | `Idempotency-Key` + `requestHash` | repeated client request protection |
| Open Banking | `bank_tran_id` | bank-side transaction identity and result check |
| Wallet reflection | `referenceType` + `referenceId` | duplicate wallet balance protection |

For wallet charge reflection:

```text
referenceType = OPEN_BANKING_CHARGE
referenceId   = bank_tran_id
```

## Sensitive Data Handling

Open Banking user tokens are stored in `open_banking_tokens` after AES-GCM encryption.

```text
accessTokenEncrypted
refreshTokenEncrypted
scope
expiresAt
userSeqNo
```

Raw account numbers are not persisted.
Manual account registration stores a SHA-256 hash for duplicate detection and exposes only a masked account number.

API logs store operational metadata and key names, not raw request/response payloads.

Stored:

- `apiName`
- `requestId`
- `bankingTransferId`
- `apiResponseCode`
- `bankResponseCode`
- request/response key list
- error message

Not stored:

- access token
- refresh token
- client secret
- raw account number
- raw Open Banking request/response payload

## APIs Without Permission

Reference APIs marked `(x)` are treated as no-permission APIs.
They are exposed only as attempt endpoints and are not connected to business state transitions.

```text
POST /bank/openbanking/attempts/real-name
POST /bank/openbanking/attempts/receive
POST /bank/openbanking/attempts/deposit-transfer
```

This prevents unverified or unauthorized external responses from changing wallet balances or transaction state.

## Trade-Offs And Next Steps

This implementation is suitable for a portfolio-grade MVP, but production hardening would still require:

- Flyway/Liquibase migrations
- token refresh flow
- external HTTP retry/timeout policy
- sandbox end-to-end profile
- richer masked API log policy
- withdrawal and compensation state machine
- stronger separation between DB transactions and external HTTP calls

## Interview Talking Points

- I separated HTTP success from financial success.
- I did not treat timeout as failure; I kept it retryable through result checks.
- I prevented wallet reflection before bank success.
- I used `bank_tran_id` and wallet references to prevent duplicate balance changes.
- I encrypted user Open Banking tokens and avoided raw account number persistence.
- I isolated no-permission APIs from business state changes.
