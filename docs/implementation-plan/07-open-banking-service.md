# 07. Open Banking Service

`banking-service`는 금융결제원 오픈뱅킹 테스트베드 또는 mock profile과 연동해 외부 은행 계좌와 PayFlow 지갑 사이의 자금 이동 상태를 관리한다.

중요: `banking-service`는 지갑 잔액을 직접 변경하지 않는다.
외부 은행 API 성공이 확정된 뒤 `wallet-service` 내부 API를 호출해 잔액 반영을 요청한다.

## 목표

구현할 기능:

```text
오픈뱅킹 계좌 등록 상태 저장
충전 계좌 목록/등록/삭제 API
충전 요청 생성
크레딧 충전 결과 조회
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

참고 기준:

```text
reference-docs/open-banking-summary.md
reference-docs/payflow-open-banking-system-flow.md
reference-docs/open-banking-postman-test-flow.md
reference-docs/open-banking-financial-knowledge.md
```

구현 관점 핵심 매핑:

```text
PayFlow 충전 = 오픈뱅킹 출금이체
PayFlow 출금 = 오픈뱅킹 입금이체
fintech_use_num = 실제 계좌번호 대신 쓰는 오픈뱅킹 계좌 식별자
bank_tran_id = PayFlow가 생성하는 외부 거래 추적 ID
api_tran_id = 오픈뱅킹센터가 응답하는 API 처리 ID
응답 불명 = 실패 확정이 아니라 결과조회 대상
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

최종 외부 API는 `docs/api-spec.md`의 Credit API를 따른다. Gateway 외부 경로는 `/api/credits/**`이고, banking-service 내부 경로는 `/credits/**`를 우선한다.

현재 Gateway에는 `/api/bank/**` 라우트가 있으므로 banking-service 구현 시 아래처럼 정리한다.

```text
MVP 권장:
/api/credits/** -> banking-service /credits/**

과도기:
/api/bank/** 라우트는 제거하거나 내부 테스트용으로만 유지한다.
```

### 충전 계좌 목록 조회

```http
GET /credits/bank-accounts
X-User-Id: 1
```

### 충전 계좌 등록

초기에는 테스트베드 또는 mock 값으로 계좌 식별자를 등록한다.

```http
POST /credits/bank-accounts
X-User-Id: 1
Content-Type: application/json

{
  "bankName": "국민",
  "accountNumber": "1234567890123",
  "accountHolderName": "홍길동"
}
```

응답:

```json
{
  "bankAccountId": "bank-account-001",
  "bankName": "국민",
  "maskedAccountNumber": "1234*********",
  "primary": true
}
```

### 충전 계좌 삭제

```http
DELETE /credits/bank-accounts/{bankAccountId}
X-User-Id: 1
```

응답: `204 No Content`

### 부모 크레딧 요약

```http
GET /credits/parent/summary
X-User-Id: 1
```

### 크레딧 충전 요청

사용자 은행 계좌에서 출금해 PayFlow 지갑에 충전한다.

```http
POST /credits/charges
X-User-Id: 1
Idempotency-Key: 20260601-user1-charge-001
Content-Type: application/json

{
  "bankAccountId": "bank-account-001",
  "amount": 10000
}
```

응답:

```json
{
  "chargeId": "charge-20260601-0001",
  "status": "PROCESSING",
  "amount": 10000
}
```

### 크레딧 충전 결과 조회

```http
GET /credits/charges/{chargeId}
X-User-Id: 1
```

응답:

```json
{
  "chargeId": "charge-20260601-0001",
  "status": "COMPLETED",
  "amount": 10000,
  "walletId": 1,
  "balanceAfter": 10000
}
```

### 지갑 출금

PayFlow 지갑 잔액을 사용자 은행 계좌로 출금한다.

초기 구현에서는 지갑 차감과 오픈뱅킹 입금이체를 하나의 분산 트랜잭션으로 묶지 않는다.
상태와 보상 근거를 남기는 방식으로 설계한다.

```http
POST /credits/withdrawals
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
userSeqNo
fintechUseNum
bankCodeStd
bankName
accountAlias
accountNumberMasked
accountHolderName
inquiryAgreeYn
transferAgreeYn
status
createdAt
updatedAt
```

오픈뱅킹 응답 필드 매핑:

```text
user_seq_no          -> userSeqNo
fintech_use_num      -> fintechUseNum
bank_code_std        -> bankCodeStd
bank_name            -> bankName
account_alias        -> accountAlias
account_num_masked   -> accountNumberMasked
account_holder_name  -> accountHolderName
inquiry_agree_yn     -> inquiryAgreeYn
transfer_agree_yn    -> transferAgreeYn
```

저장 원칙:

```text
계좌번호 원문은 저장하지 않는다.
fintechUseNum과 마스킹 계좌번호를 저장한다.
출금이체 기반 충전을 하려면 transferAgreeYn = Y 여야 한다.
조회 API를 사용하려면 inquiryAgreeYn = Y 여야 한다.
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
bankAccountId
amount
status
idempotencyKey
requestHash
bankTranId
bankTranDate
tranDtime
apiTranId
apiTranDtm
apiRspCode
bankRspCode
failureReason
walletReferenceType
walletReferenceId
resultCheckCount
nextResultCheckAt
lastResultCheckedAt
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
BANK_REQUESTED
BANK_PROCESSING
BANK_SUCCEEDED
WALLET_REFLECTING
COMPLETED
FAILED
UNKNOWN
BANK_SUCCESS_BUT_WALLET_FAILED
COMPENSATION_REQUIRED
```

상태 의미:

```text
REQUESTED              PayFlow가 요청을 접수하고 DB에 저장함
BANK_REQUESTED         오픈뱅킹 요청 직전 또는 요청 중
BANK_PROCESSING        은행/오픈뱅킹이 처리 중이라고 응답함
BANK_SUCCEEDED         은행 거래 성공이 확정됨, wallet 반영 전
WALLET_REFLECTING      wallet-service 입금/차감 반영 중
COMPLETED              은행 거래와 wallet 반영까지 완료
FAILED                 은행 거래 실패 또는 wallet 차감 전 실패
UNKNOWN                timeout/응답 미수신 등 결과조회 필요
BANK_SUCCESS_BUT_WALLET_FAILED 은행 성공은 확정됐지만 wallet-service 반영 실패, 재처리 필요
COMPENSATION_REQUIRED  이미 돈이 한쪽에서 움직여 보상/재처리 필요
```

### BankingApiLog

```text
id
bankingTransferId
apiName
requestId
httpStatus
apiRspCode
bankRspCode
requestPayloadMasked
responsePayloadMasked
errorMessage
createdAt
```

로그 원칙:

```text
access_token 원문 저장 금지
refresh_token 원문 저장 금지
client_secret 원문 저장 금지
계좌번호 원문 저장 금지
입금이체용 암호문구 원문 저장 금지
요청/응답 전문은 마스킹 후 저장한다.
```

### OpenBankingToken, 실제 사용자 인증까지 구현할 경우

초기 MVP에서는 `.env`의 테스트베드 토큰 또는 mock token으로 시작한다.
사용자 인증과 토큰 갱신까지 구현할 때만 별도 테이블을 둔다.

```text
id
userId
tokenType
userSeqNo
clientUseCode
accessTokenEncrypted
refreshTokenEncrypted
scope
expiresAt
createdAt
updatedAt
```

TokenType:

```text
ORG
USER
```

## 오픈뱅킹 API 매핑

### 충전: 출금이체

PayFlow에서 부모 크레딧을 충전하는 흐름은 사용자의 은행계좌에서 PayFlow 약정계좌로 돈을 가져오는 것이므로 오픈뱅킹 출금이체 API를 사용한다.

```text
POST /v2.0/transfer/withdraw/fin_num
POST /v2.0/transfer/withdraw/acnt_num
```

MVP에서는 `fin_num` 방식을 우선한다.

주요 요청 필드:

```text
bank_tran_id
cntr_account_type
cntr_account_num
dps_print_content
fintech_use_num
wd_print_content
tran_amt
tran_dtime
req_client_name
req_client_fintech_use_num
req_client_num
transfer_purpose
```

주요 응답 필드:

```text
api_tran_id
api_tran_dtm
rsp_code
rsp_message
bank_tran_id
bank_tran_date
bank_code_tran
bank_rsp_code
bank_rsp_message
fintech_use_num
account_holder_name
tran_amt
wd_limit_remain_amt
```

### 출금: 입금이체

PayFlow 지갑 잔액을 사용자 은행계좌로 보내는 흐름은 오픈뱅킹 입금이체 API를 사용한다.

```text
POST /v2.0/transfer/deposit/fin_num
POST /v2.0/transfer/deposit/acnt_num
```

MVP에서는 `fin_num` 방식을 우선한다.

주요 요청 필드:

```text
cntr_account_type
cntr_account_num
wd_pass_phrase
wd_print_content
name_check_option
tran_dtime
req_cnt
req_list[].tran_no
req_list[].bank_tran_id
req_list[].fintech_use_num
req_list[].print_content
req_list[].tran_amt
req_list[].req_client_name
req_list[].req_client_fintech_use_num
req_list[].req_client_num
req_list[].transfer_purpose
req_list[].cms_num
req_list[].withdraw_bank_tran_id
req_list[].recv_bank_tran_id
```

`recv_bank_tran_id`는 수취조회를 먼저 수행한 경우에만 사용한다. MVP에서는 선택 필드로 둔다.

### 이체결과조회

응답 불명, 처리 중, 중복 거래 응답은 실패 확정이 아니라 결과조회 대상이다.

```text
POST /v2.0/transfer/result
```

주요 요청 필드:

```text
check_type
tran_dtime
req_cnt
req_list[].tran_no
req_list[].org_bank_tran_id
req_list[].org_bank_tran_date
req_list[].org_tran_amt
```

`check_type`:

```text
1 = 출금이체 결과조회, PayFlow 충전 결과조회
2 = 입금이체 결과조회, PayFlow 출금 결과조회
```

결과조회에 필요한 값은 `banking_transfers`에 반드시 저장한다.

```text
org_bank_tran_id   = bankTranId
org_bank_tran_date = bankTranDate
org_tran_amt       = amount
```

## 충전 처리 흐름

```text
1. Idempotency-Key header 확인
2. 요청 body hash 계산
3. 같은 key가 있으면 기존 결과 또는 409 반환
4. BankAccount 소유권과 상태 확인
   - status = ACTIVE
   - transferAgreeYn = Y
5. bank_tran_id와 tran_dtime 생성
6. BankingTransfer REQUESTED 저장
   - bankTranId unique
   - tranDtime 저장
7. BankingTransfer BANK_REQUESTED 변경
8. 오픈뱅킹 출금이체 API 호출
9. BankingApiLog 저장
   - request/response payload는 마스킹
10. 성공 응답이면 BANK_SUCCEEDED 저장
   - apiTranId, apiTranDtm, bankTranDate 저장
   - apiRspCode = A0000
   - bankRspCode = 000
11. wallet-service deposit 호출
   - referenceType: OPEN_BANKING_CHARGE
   - referenceId: bankTranId
12. wallet 반영 성공이면 COMPLETED 저장
13. wallet 반영 실패이면 BANK_SUCCESS_BUT_WALLET_FAILED 저장
    - nextResultCheckAt 설정
    - failureReason 저장
14. 응답 반환
```

응답 불명/처리 중:

```text
timeout, 응답 미수신, A0001, A0007, bank_rsp_code 400/803/804/819/822 등은 FAILED로 확정하지 않는다.
UNKNOWN 또는 BANK_PROCESSING으로 저장한다.
nextResultCheckAt과 resultCheckCount를 설정해 결과조회 워커 대상에 넣는다.
```

은행 성공 후 wallet 반영 실패:

```text
BANK_SUCCEEDED 또는 WALLET_REFLECTING 상태에서 wallet-service deposit이 실패하면 BANK_SUCCESS_BUT_WALLET_FAILED로 둔다.
walletReferenceType = OPEN_BANKING_CHARGE, walletReferenceId = bankTranId로 저장한다.
재처리 워커는 같은 bankTranId로 wallet-service deposit을 다시 호출한다.
wallet-service는 referenceType/referenceId unique 제약으로 중복 입금을 막아야 한다.
재처리 성공 시 COMPLETED로 변경한다.
```

## 출금 처리 흐름

초기 정책:

```text
1. Idempotency-Key header 확인
2. 요청 body hash 계산
3. BankAccount 소유권과 상태 확인
4. bank_tran_id와 tran_dtime 생성
5. BankingTransfer REQUESTED 저장
6. wallet-service withdraw 호출
   - referenceType: OPEN_BANKING_WITHDRAWAL
   - referenceId: bankTranId
7. wallet 차감 성공 후 BANK_REQUESTED 또는 BANK_PROCESSING 저장
8. 오픈뱅킹 입금이체 API 호출
9. BankingApiLog 저장
10. 입금이체 성공이면 COMPLETED 저장
    - apiTranId, apiTranDtm, bankTranDate 저장
11. 입금이체 실패 또는 응답 불명이면 COMPENSATION_REQUIRED 또는 UNKNOWN 저장
12. 복구 배치나 운영자 API에서 wallet 보상 입금 근거로 사용
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
  redirect-uri: ${OPENBANKING_REDIRECT_URI:}
  cntr-account-type: ${OPENBANKING_CNTR_ACCOUNT_TYPE:N}
  cntr-account-num: ${OPENBANKING_CNTR_ACCOUNT_NUM:}
  wd-pass-phrase: ${OPENBANKING_WD_PASS_PHRASE:}
```

민감정보 관리:

```text
client-secret, access-token, refresh-token, cntr-account-num, wd-pass-phrase는 로그에 원문으로 남기지 않는다.
prod profile은 초기 포트폴리오 범위에서 사용하지 않는다.
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

## bank_tran_id 규칙

`bank_tran_id`는 오픈뱅킹 거래 추적과 중복 방어의 핵심이다.

```text
항상 서버에서 생성한다.
거래마다 새 값을 만든다.
오픈뱅킹 요청 전에 DB에 저장한다.
DB unique 제약을 둔다.
응답 여부와 무관하게 보존한다.
wallet referenceId에는 bankTranId를 우선 사용한다.
```

형식 예시:

```text
F + 이용기관코드 숫자 9자리 + U + 임의 문자열 9자리
예: F123456789U4BC34241Z
```

`tran_dtime` 형식:

```text
yyyyMMddHHmmss
예: 20260601143000
```

`bank_tran_date` 형식:

```text
yyyyMMdd
예: 20260601
```

## 응답코드 판정

HTTP 200은 업무 성공을 의미하지 않는다. 반드시 본문 코드를 본다.

성공:

```text
rsp_code = A0000
bank_rsp_code = 000
```

결과조회 필요:

```text
rsp_code = A0001 처리 중
rsp_code = A0007 처리시간 초과
bank_rsp_code = 400 입금 처리 중
bank_rsp_code = 803 내부 처리 에러
bank_rsp_code = 804 처리시간 초과
bank_rsp_code = 819 내부 전문 송신 실패
bank_rsp_code = 822 거래고유번호 중복
응답 미수신
timeout
```

명시 실패 예시:

```text
A0312 예금주명 불일치
A0316 제3자정보제공동의 만료
A0319 출금동의 만료
A0323 이용기관에 등록된 사용자 계좌 아님
453 예금잔액 부족
454 출금가능잔액 부족
455 건별 이체한도 초과
456 일일 이체한도 초과
464 사용자 등록 정보 이상
490 오픈뱅킹 안심차단 중인 사용자
```

상태 매핑:

```text
충전 성공:
  BANK_SUCCEEDED -> wallet deposit -> COMPLETED

충전 결과조회 필요:
  UNKNOWN 또는 BANK_PROCESSING

충전 실패:
  FAILED

은행 성공 후 wallet 반영 실패:
  BANK_SUCCESS_BUT_WALLET_FAILED

출금 성공:
  COMPLETED

출금 결과조회 필요:
  UNKNOWN 또는 BANK_PROCESSING

출금에서 wallet 차감 후 입금이체 실패:
  COMPENSATION_REQUIRED
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
  BANK_SUCCESS_BUT_WALLET_FAILED
  wallet-service referenceId 기준으로 재반영 가능
```

결과조회 워커는 `UNKNOWN`, `BANK_PROCESSING` 상태를 주기적으로 확인한다.

```text
1. status in (UNKNOWN, BANK_PROCESSING) 조회
2. nextResultCheckAt <= now 인 거래만 선점
3. transferType으로 check_type 결정
   - CHARGE -> 1
   - WITHDRAWAL -> 2
4. bankTranId, bankTranDate, amount로 이체결과조회 요청
5. BankingApiLog 저장
6. 최종 성공 + CHARGE이면 BANK_SUCCEEDED 후 wallet deposit
7. 최종 성공 + WITHDRAWAL이면 COMPLETED
8. 최종 실패 + CHARGE이면 FAILED
9. 최종 실패 + WITHDRAWAL이면 COMPENSATION_REQUIRED
10. 계속 처리 중이면 resultCheckCount 증가, nextResultCheckAt을 뒤로 미룸
11. 재시도 한도 초과 시 운영자 확인 필요 상태로 남김
```

wallet 반영 재처리 워커는 `BANK_SUCCESS_BUT_WALLET_FAILED` 상태를 주기적으로 확인한다.

```text
1. status = BANK_SUCCESS_BUT_WALLET_FAILED 조회
2. nextResultCheckAt <= now 인 거래만 대상으로 잡음
3. DB transaction 안에서 status를 WALLET_REFLECTING으로 변경해 선점
4. 이미 다른 worker가 선점해 status가 바뀐 건은 skip
5. walletReferenceType = OPEN_BANKING_CHARGE, walletReferenceId = bankTranId로 deposit 재호출
6. 성공하면 COMPLETED
7. 실패하면 BANK_SUCCESS_BUT_WALLET_FAILED로 되돌리고 resultCheckCount 증가, nextResultCheckAt을 뒤로 미룸
8. 반복 실패 시 운영자 확인 대상으로 남김
```

선점 쿼리는 `id`, `status`, `updatedAt` 조건을 함께 사용한다.
여러 banking-service 인스턴스가 떠 있어도 같은 거래를 동시에 재처리하지 않도록 optimistic update 또는 pessimistic lock 중 하나를 적용한다.

재처리 한도 초과 정책:

```text
초기 maxResultCheckCount = 10
resultCheckCount >= maxResultCheckCount이면 상태는 BANK_SUCCESS_BUT_WALLET_FAILED로 유지한다.
다만 worker 자동 조회 대상에서는 제외하고 운영자 확인 대상으로 분류한다.
운영자 재처리 API를 만들 경우 resultCheckCount를 유지한 채 수동 deposit 재호출 이력을 BankingApiLog 또는 별도 운영 로그에 남긴다.
```

결과조회 재시도 간격은 초기에는 단순 정책으로 둔다.

```text
1회차: 1분 뒤
2회차: 2분 뒤
3회차: 4분 뒤
이후: 10분 간격 또는 운영자 확인
```

## 구현 순서

1. MockOpenBankingClient부터 구현
   - 성공, 명시 실패, timeout, 처리 중, bank_tran_id 중복을 시뮬레이션한다.
   - 외부 은행망 없이 상태 전이와 테스트를 먼저 닫는다.

2. 충전만 먼저 완성
   - 충전 계좌 목록/등록/삭제 API 구현
   - 부모 크레딧 요약 API 구현
   - 충전 요청 생성
   - 충전 결과 조회 API 구현
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
   - `BANK_SUCCESS_BUT_WALLET_FAILED` 상태는 은행 결과조회가 아니라 wallet deposit 재시도 대상으로 처리한다.

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
계좌 목록 조회 성공
계좌 삭제 성공
부모 크레딧 요약 조회 성공
타인 walletId 계좌 등록 실패
MockOpenBankingClient 성공 응답 시 은행 성공 상태 저장
MockOpenBankingClient 명시 실패 응답 시 FAILED
MockOpenBankingClient timeout 응답 시 UNKNOWN
MockOpenBankingClient 처리 중 응답 시 BANK_PROCESSING
MockOpenBankingClient bank_tran_id 중복 응답 시 결과조회 대상으로 전환
충전 성공 시 BankingTransfer COMPLETED
충전 결과 조회 시 상태와 balanceAfter 반환
충전 성공 시 wallet-service deposit 호출
같은 Idempotency-Key 재요청 시 같은 응답
같은 Idempotency-Key 다른 body 요청 시 409
bank_tran_id 중복 저장 방지
오픈뱅킹 명시적 실패 시 FAILED
오픈뱅킹 timeout 시 UNKNOWN
A0007 또는 입금 처리 중 응답 시 결과조회 대상으로 전환
결과조회 성공 응답 시 최종 상태 갱신
은행 성공 후 wallet 반영 실패 시 BANK_SUCCESS_BUT_WALLET_FAILED
wallet 반영 재시도 시 reference 기반 중복 증가 없음
출금 요청에서 wallet 차감 실패 시 FAILED
출금 입금이체 실패 시 COMPENSATION_REQUIRED
```
