# 포트폴리오 노트: 오픈뱅킹 연동

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

이 문서는 PayFlow의 오픈뱅킹 연동을 포트폴리오와 면접 관점에서 정리한 것이다.
핵심은 "외부 API를 호출했다"가 아니라, **금융 거래의 불확실성, 멱등성, 민감정보 처리를 코드로 명시했다**는 점이다.

## 문제

오픈뱅킹 이체 API는 일반적인 CRUD API처럼 동작하지 않는다.
HTTP 200은 API 서버가 응답을 반환했다는 의미일 뿐, 은행 이체가 최종 확정되었다는 뜻은 아닐 수 있다.

PayFlow는 아래 상황을 별도 상태로 다룬다.

- 최종 성공
- 명시적 실패
- 처리 중
- 타임아웃 또는 모호한 응답
- 중복 거래 식별자

## 구현 흐름

### 계좌 연결

```text
GET  /bank/openbanking/authorize-url
POST /bank/openbanking/callback
POST /bank/openbanking/accounts/sync
```

콜백 처리 흐름:

```text
authorization code
-> /oauth/2.0/token
-> 암호화된 토큰 저장
-> /v2.0/user/me
-> bank_accounts 동기화
```

서비스는 `fintechUseNum`, `userSeqNo`, 은행 코드, 은행명, 마스킹 계좌번호, 예금주명, 동의 여부 같은 계좌 메타데이터를 저장한다.
인가 코드 원문, 토큰 원문, 계좌번호 원문은 저장하지 않는다.

### 지갑 충전

PayFlow의 충전은 오픈뱅킹 출금이체에 매핑된다.

```text
POST /bank/deposits
-> /v2.0/transfer/withdraw/fin_num
-> 은행 성공 확정
-> wallet-service 입금 반영
```

상태 흐름:

```text
REQUESTED
-> BANK_PROCESSING
-> BANK_SUCCEEDED
-> WALLET_REFLECTING
-> COMPLETED
```

지갑 잔액은 은행 성공이 확인된 뒤에만 반영한다.

### 결과 조회

모호한 거래는 즉시 실패로 처리하지 않는다.
재시도 가능한 상태로 남기고, 이체 결과 조회를 통해 최종 상태를 확정한다.

```text
POST /bank/transfers/{bankingTransferId}/result-check
OpenBankingResultCheckScheduler
```

스케줄러는 `nextResultCheckAt <= now` 조건을 만족하는 `BANK_PROCESSING`, `UNKNOWN` 거래를 조회한다.

### 지갑 출금과 보상

PayFlow 출금은 오픈뱅킹 입금이체 API 호출을 시도한다.
다만 참고 명세에서 해당 API가 `(x)`로 표시되어 있어, 이 환경에서는 권한이 없는 API로 취급한다.
따라서 해당 응답은 비즈니스 상태 확정에 사용하지 않는다.

```text
POST /bank/withdrawals
-> wallet-service withdraw
-> /v2.0/transfer/deposit/fin_num 호출 시도
-> COMPENSATION_REQUIRED
```

외부 은행 시도 전에 지갑 출금이 실패하면 거래는 `FAILED`가 된다.
지갑 출금은 성공했지만 권한 없는 은행 이체 응답을 신뢰할 수 없으면 `COMPENSATION_REQUIRED`로 격리한다.

수동 보상은 원래의 `bank_tran_id`를 멱등 참조값으로 사용해 지갑 입금 방식으로 환불한다.

```text
POST /bank/transfers/{bankingTransferId}/compensate
referenceType = OPEN_BANKING_REFUND
referenceId   = bank_tran_id
```

## 멱등성 전략

| 계층 | 키 | 목적 |
|---|---|---|
| API 요청 | `Idempotency-Key` + `requestHash` | 클라이언트 반복 요청 방어 |
| 오픈뱅킹 | `bank_tran_id` | 은행 측 거래 식별과 결과 조회 기준 |
| 지갑 반영 | `referenceType` + `referenceId` | 지갑 잔액 중복 반영 방지 |

지갑 충전 반영:

```text
referenceType = OPEN_BANKING_CHARGE
referenceId   = bank_tran_id
```

출금 보상:

```text
referenceType = OPEN_BANKING_REFUND
referenceId   = bank_tran_id
```

## 민감정보 처리

오픈뱅킹 사용자 토큰은 AES-GCM 암호화 후 `open_banking_tokens`에 저장한다.

```text
accessTokenEncrypted
refreshTokenEncrypted
scope
expiresAt
userSeqNo
```

계좌번호 원문은 저장하지 않는다.
수기 계좌 등록은 중복 확인을 위해 SHA-256 해시를 저장하고, 화면과 응답에는 마스킹 계좌번호만 노출한다.

API 로그에는 운영 메타데이터와 키 목록만 저장하고, 요청/응답 원문 payload는 저장하지 않는다.

저장하는 값:

- `apiName`
- `requestId`
- `bankingTransferId`
- `apiResponseCode`
- `bankResponseCode`
- 요청/응답 키 목록
- 오류 메시지

저장하지 않는 값:

- access token
- refresh token
- client secret
- 계좌번호 원문
- 오픈뱅킹 요청/응답 원문 payload

## 권한 없는 API 처리

참고 명세에서 `(x)`로 표시된 API는 권한 없는 API로 취급한다.
해당 API는 시도 엔드포인트로만 노출하고, 비즈니스 상태 전이와 연결하지 않는다.

```text
POST /bank/openbanking/attempts/real-name
POST /bank/openbanking/attempts/receive
POST /bank/openbanking/attempts/deposit-transfer
```

이렇게 분리하면 검증되지 않았거나 권한 없는 외부 응답이 지갑 잔액이나 거래 상태를 변경하지 못한다.

## 트레이드오프와 다음 작업

현재 구현은 포트폴리오용 MVP에는 적합하지만, 운영 수준으로 보강하려면 아래 작업이 필요하다.

- Flyway 또는 Liquibase 기반 마이그레이션
- 토큰 갱신 흐름
- 외부 HTTP 재시도/타임아웃 정책
- 샌드박스 E2E 프로필
- 더 세밀한 마스킹 API 로그 정책
- DB 트랜잭션과 외부 HTTP 호출의 더 강한 분리

## 면접에서 설명할 내용

- HTTP 성공과 금융 거래 성공을 분리했다.
- 타임아웃을 실패로 단정하지 않고, 결과 조회로 재시도 가능한 상태를 유지했다.
- 은행 성공 확정 전에는 지갑 잔액을 반영하지 않았다.
- `bank_tran_id`와 지갑 참조값으로 중복 잔액 변경을 방지했다.
- 오픈뱅킹 사용자 토큰을 암호화하고 계좌번호 원문 저장을 피했다.
- 권한 없는 API를 비즈니스 상태 전이에서 격리했다.
- 권한 없는 은행 API가 성공했다고 가정하지 않고, 출금 보상을 명시적으로 모델링했다.
