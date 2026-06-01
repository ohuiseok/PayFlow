# 05. Open Banking Service

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
```

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
}
```

구현:

```text
KftcOpenBankingClient
MockOpenBankingClient
```

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

은행 성공 후 wallet 반영 실패:
  COMPENSATION_REQUIRED
  wallet-service referenceId 기준으로 재반영 가능
```

## 구현 순서

1. banking-service 프로젝트 생성
2. application.yml과 profile 구성
3. BankAccount 엔티티 작성
4. BankingTransfer 엔티티 작성
5. BankingApiLog 엔티티 작성
6. Repository 작성
7. request hash와 IdempotencyService 작성
8. OpenBankingClient 인터페이스 작성
9. MockOpenBankingClient 작성
10. WalletClient 작성
11. 충전 API 구현
12. 충전 성공 후 wallet-service deposit 연동
13. 응답 불명/실패 상태 저장
14. 출금 상태 모델과 최소 API 구현
15. 테스트 작성

## 테스트

필수 테스트:

```text
계좌 등록 성공
타인 walletId 계좌 등록 실패
충전 성공 시 BankingTransfer COMPLETED
충전 성공 시 wallet-service deposit 호출
같은 Idempotency-Key 재요청 시 같은 응답
같은 Idempotency-Key 다른 body 요청 시 409
오픈뱅킹 명시적 실패 시 FAILED
오픈뱅킹 timeout 시 UNKNOWN
은행 성공 후 wallet 반영 실패 시 COMPENSATION_REQUIRED
wallet 반영 재시도 시 reference 기반 중복 증가 없음
출금 요청에서 wallet 차감 실패 시 FAILED
출금 입금이체 실패 시 COMPENSATION_REQUIRED
```

