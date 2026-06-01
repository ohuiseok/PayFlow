# 07. Open Banking Service

`banking-service`는 금융결제원 오픈뱅킹 테스트베드 또는 mock profile과 연동해 외부 은행 계좌와 PayFlow 지갑 사이의 자금 이동 상태를 관리한다.

중요: `banking-service`는 지갑 잔액을 직접 변경하지 않는다.
외부 은행 API 성공이 확정된 뒤 `wallet-service` 내부 API를 호출해 잔액 반영을 요청한다.

## 목표

구현할 기능:

```text
오픈뱅킹 계좌 등록 상태 저장
충전 요청 생성
오픈뱅킹 출금이체 요청
출금이체 성공 후 wallet-service 충전 요청
지갑 출금/환불 요청 생성
오픈뱅킹 입금이체 요청
외부 거래 추적 ID 저장
응답 불명/실패 상태 기록
Idempotency-Key 기반 중복 요청 방지
mock profile 제공
```

## 포트폴리오 범위

초기 구현은 테스트베드 또는 mock adapter를 대상으로 한다.

```text
구현함:
- 오픈뱅킹 API client 인터페이스
- 테스트베드 profile 설정
- mock profile 설정
- bank_tran_id/api_tran_id 저장
- 응답 코드별 상태 전이
- 외부 성공 확정 후 wallet-service 반영

구현하지 않음:
- 운영 은행망 실거래
- 이용기관 심사/계약 자동화
- 실제 계좌번호 원문 저장
- 복잡한 전자서명/인증서 운용
- 대량 이체 운영 업무
- 정보제공자 API
```

정보제공자 API는 PayFlow가 선불전자지급수단 발행자로서 외부 이용기관에 선불목록조회, 선불잔액조회,
선불거래내역조회를 제공하는 단계에서 필요하다. 초기 구현에서는 오픈뱅킹 "이용기관" 기능을 먼저 완성하고,
정보제공자 기능은 별도 `provider-api` 또는 `banking-service`의 inbound adapter로 분리하는 것을 후순위로 둔다.

## 서비스 경계

```text
user-service
- 사용자 인증과 JWT 발급

wallet-service
- 내부 지갑 잔액의 진실
- 잔액 변경 이력
- reference 기반 멱등성

banking-service
- 외부 은행망 요청 상태의 진실
- 오픈뱅킹 access token 관리
- bank_tran_id/api_tran_id 저장
- 외부 성공 후 wallet-service 호출

transfer-service
- PayFlow 지갑 간 내부 송금 상태
```

## API

### 계좌 등록

초기에는 테스트베드 또는 mock 값으로 계좌 식별자를 등록한다.

```http
POST /bank/accounts
X-User-Id: 1
Content-Type: application/json

{
  "walletId": 1,
  "bankCode": "097",
  "accountNumber": "1234567890123",
  "accountHolderName": "홍길동",
  "fintechUseNum": "199166681057555555555555"
}
```

응답:

```json
{
  "bankAccountId": 1,
  "userId": 1,
  "walletId": 1,
  "bankCode": "097",
  "accountNumberMasked": "1234*********",
  "status": "ACTIVE"
}
```

### 지갑 충전

사용자 은행 계좌에서 출금해 PayFlow 지갑에 충전한다.

```http
POST /bank/charges
X-User-Id: 1
Idempotency-Key: 20260601-user1-charge-001
Content-Type: application/json

{
  "walletId": 1,
  "bankAccountId": 1,
  "amount": 10000
}
```

응답:

```json
{
  "bankingTransferId": 1001,
  "status": "COMPLETED",
  "amount": 10000,
  "walletId": 1,
  "bankTranId": "M20260601123456789"
}
```

### 지갑 출금

PayFlow 지갑 잔액을 사용자 은행 계좌로 출금한다.

초기 구현에서는 지갑 차감과 오픈뱅킹 입금이체를 하나의 분산 트랜잭션으로 묶지 않는다.
상태와 보상 근거를 남기는 방식으로 설계한다.

```http
POST /bank/withdrawals
X-User-Id: 1
Idempotency-Key: 20260601-user1-withdrawal-001
Content-Type: application/json

{
  "walletId": 1,
  "bankAccountId": 1,
  "amount": 5000
}
```

## 엔티티

### BankAccount

```text
id
userId
walletId
bankCode
accountNumberMasked
accountHolderName
fintechUseNum
status
createdAt
updatedAt
```

BankAccountStatus:

```text
ACTIVE
LOCKED
DELETED
```

### BankingTransfer

```text
id
transferType
userId
walletId
amount
status
idempotencyKey
requestHash
bankTranId
apiTranId
apiResponseCode
bankResponseCode
failureReason
walletReferenceId
requestedAt
completedAt
createdAt
updatedAt
```

BankingTransferType:

```text
CHARGE
WITHDRAWAL
REFUND
```

BankingTransferStatus:

```text
REQUESTED
BANK_PROCESSING
BANK_SUCCEEDED
WALLET_REFLECTING
COMPLETED
FAILED
UNKNOWN
COMPENSATION_REQUIRED
```

### BankingApiLog

```text
id
bankingTransferId
apiName
requestId
responseCode
bankResponseCode
rawResponse
createdAt
```

## 충전 처리 흐름

```text
1. Idempotency-Key header 확인
2. 요청 body hash 계산
3. 같은 key가 있으면 기존 결과 또는 409 반환
4. BankAccount 소유권과 상태 확인
5. BankingTransfer REQUESTED 저장
6. bank_tran_id 생성
7. 오픈뱅킹 출금이체 API 호출
8. 성공 응답이면 BANK_SUCCEEDED 저장
9. wallet-service deposit 호출
   - referenceType: OPEN_BANKING_CHARGE
   - referenceId: bankTranId 또는 apiTranId
10. wallet 반영 성공이면 COMPLETED 저장
11. 응답 반환
```

## 출금 처리 흐름

초기 정책:

```text
1. Idempotency-Key header 확인
2. 요청 body hash 계산
3. BankAccount 소유권과 상태 확인
4. wallet-service withdraw 호출
   - referenceType: OPEN_BANKING_WITHDRAWAL
   - referenceId: bankingTransferId 또는 bankTranId
5. wallet 차감 성공 후 오픈뱅킹 입금이체 API 호출
6. 입금이체 성공이면 COMPLETED 저장
7. 입금이체 실패 또는 응답 불명이면 COMPENSATION_REQUIRED 또는 UNKNOWN 저장
8. 복구 배치나 운영자 API에서 wallet 보상 입금 근거로 사용
```

주의:

```text
출금은 외부 은행망 실패 시 보상이 필요하므로 충전보다 위험하다.
1차 구현에서는 충전을 먼저 완성하고, 출금은 상태 모델과 문서/테스트를 우선한다.
```

## 오픈뱅킹 Client

위치:

```text
banking-service/src/main/java/com/payflow/banking/infrastructure/openbanking
```

인터페이스:

```java
public interface OpenBankingClient {
    OpenBankingWithdrawResponse requestWithdraw(OpenBankingWithdrawRequest request);
    OpenBankingDepositResponse requestDeposit(OpenBankingDepositRequest request);
    OpenBankingTransferResultResponse getTransferResult(OpenBankingTransferResultRequest request);
}
```

구현:

```text
KftcOpenBankingClient
MockOpenBankingClient
```

`MockOpenBankingClient`는 실제 구현의 첫 단계로 만든다.

시뮬레이션해야 하는 응답:

```text
성공
명시 실패
timeout 또는 네트워크 단절
처리 중
bank_tran_id 중복
```

mock 응답은 충전/출금 서비스 테스트에서 외부 은행망 없이 상태 전이를 검증하는 기준으로 사용한다.

profile:

```text
local: mock adapter 기본 사용
kftc-testbed: 금융결제원 테스트베드 사용
prod: 사용하지 않음
```

환경변수:

```yaml
openbanking:
  base-url: ${OPENBANKING_BASE_URL:https://testapi.openbanking.or.kr}
  client-id: ${OPENBANKING_CLIENT_ID:}
  client-secret: ${OPENBANKING_CLIENT_SECRET:}
  client-use-code: ${OPENBANKING_CLIENT_USE_CODE:}
  access-token: ${OPENBANKING_ACCESS_TOKEN:}
```

## 멱등성 규칙

```text
Idempotency-Key는 충전/출금 요청마다 필수다.
같은 key + 같은 body는 기존 응답을 반환한다.
같은 key + 다른 body는 409 Conflict를 반환한다.
bank_tran_id는 UNIQUE로 저장한다.
wallet-service 반영은 walletReferenceId로 중복 방어한다.
```

멱등성은 세 겹으로 둔다.

```text
1. Idempotency-Key + requestHash
   - API 재요청 방어

2. bank_tran_id UNIQUE
   - 은행망 거래 중복 방어

3. wallet referenceType/referenceId
   - 지갑 잔액 중복 반영 방어
```

## 실패와 응답 불명

오픈뱅킹 호출은 아래처럼 처리한다.

```text
명시적 성공:
  BANK_SUCCEEDED -> wallet-service 반영

명시적 실패:
  FAILED
  failureReason/apiResponseCode/bankResponseCode 저장

timeout 또는 네트워크 단절:
  UNKNOWN
  bank_tran_id 기준으로 조회/대사 필요

처리 중 또는 중복 bank_tran_id:
  BANK_PROCESSING 또는 UNKNOWN
  이체결과조회 API로 최종 결과 확인

은행 성공 후 wallet 반영 실패:
  COMPENSATION_REQUIRED
  wallet-service referenceId 기준으로 재반영 가능
```

결과조회 워커는 `UNKNOWN`, `BANK_PROCESSING` 상태를 주기적으로 확인한다.

```text
1. bank_tran_id, bank_tran_date, amount로 이체결과조회 요청
2. 최종 성공이면 BANK_SUCCEEDED 또는 COMPLETED로 전이
3. 최종 실패이면 FAILED 또는 COMPENSATION_REQUIRED로 전이
4. 계속 처리 중이면 nextResultCheckAt을 뒤로 미룸
5. 재시도 한도 초과 시 운영자 확인 필요 상태로 남김
```

## 구현 순서

1. MockOpenBankingClient부터 구현
   - 성공, 명시 실패, timeout, 처리 중, bank_tran_id 중복을 시뮬레이션한다.
   - 외부 은행망 없이 상태 전이와 테스트를 먼저 닫는다.

2. 충전만 먼저 완성
   - 계좌 등록 상태 저장
   - 충전 요청 생성
   - 오픈뱅킹 출금이체 성공 확정
   - wallet-service deposit 호출
   - `COMPLETED`까지 닫힌 루프로 만든다.

3. 멱등성 구현
   - `Idempotency-Key + requestHash`로 API 재요청을 방어한다.
   - `bank_tran_id` unique 제약으로 은행망 중복을 방어한다.
   - wallet `referenceType/referenceId`로 지갑 중복 반영을 방어한다.

4. 결과조회 워커 추가
   - `UNKNOWN`, `BANK_PROCESSING` 상태를 주기적으로 조회한다.
   - `/transfer/result` 결과에 따라 성공, 실패, 보상 필요 상태로 전이한다.

5. 출금 API 추가
   - 초기에는 wallet 차감 후 오픈뱅킹 입금이체를 요청한다.
   - 입금이체 실패 또는 응답 불명 시 보상 근거를 남긴다.
   - 이후 지갑 hold 모델로 확장할 수 있도록 상태를 분리한다.

6. 정보제공자 API는 2차 범위로 분리
   - 선불목록조회, 선불잔액조회, 선불거래내역조회 제공은 초기 구현 대상이 아니다.
   - 필요 시 별도 `provider-api` 또는 inbound adapter로 설계한다.

## 테스트

필수 테스트:

```text
계좌 등록 성공
타인 walletId 계좌 등록 실패
MockOpenBankingClient 성공 응답 시 은행 성공 상태 저장
MockOpenBankingClient 명시 실패 응답 시 FAILED
MockOpenBankingClient timeout 응답 시 UNKNOWN
MockOpenBankingClient 처리 중 응답 시 BANK_PROCESSING
MockOpenBankingClient bank_tran_id 중복 응답 시 결과조회 대상으로 전환
충전 성공 시 BankingTransfer COMPLETED
충전 성공 시 wallet-service deposit 호출
같은 Idempotency-Key 재요청 시 같은 응답
같은 Idempotency-Key 다른 body 요청 시 409
bank_tran_id 중복 저장 방지
오픈뱅킹 명시적 실패 시 FAILED
오픈뱅킹 timeout 시 UNKNOWN
A0007 또는 입금 처리 중 응답 시 결과조회 대상으로 전환
결과조회 성공 응답 시 최종 상태 갱신
은행 성공 후 wallet 반영 실패 시 COMPENSATION_REQUIRED
wallet 반영 재시도 시 reference 기반 중복 증가 없음
출금 요청에서 wallet 차감 실패 시 FAILED
출금 입금이체 실패 시 COMPENSATION_REQUIRED
```
