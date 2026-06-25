# PayFlow Service Flow

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

PayFlow는 HTTP 동기 호출과 Kafka 이벤트를 함께 사용해 정책 미션 지원금 지급 흐름을 완성한다.

## 1. Signup

```text
Client
-> api-gateway
-> user-service
   -> users 생성
   -> wallet-service /internal/wallets 호출
      -> wallets 생성
```

완료 조건: 기관 담당자와 청년 참여자 모두 지갑이 생성된다.

## 2. 기관 지원금 예산 충전

### Toss PG 충전

```text
Client
-> POST /api/payments/toss/charges
   -> payment_charges READY 생성
   -> toss_payment_orders READY 생성

Client
-> POST /api/payments/toss/confirm
   -> Toss 승인 API 호출
   -> wallet-service credit 호출
   -> ledger-service payment ledger 기록
```

지갑 반영 실패 시 `COMPENSATION_REQUIRED`로 남기고 운영 API로 재입금한다.

### Open Banking 충전

```text
Client
-> POST /api/bank/deposits
   -> banking_transfers REQUESTED 생성
   -> 오픈뱅킹 입금이체 호출
   -> 성공 시 wallet-service credit
   -> COMPLETED
```

처리 중 응답은 스케줄러가 결과 조회 API로 최종화한다.

## 3. 기관-청년 참여자 연결

```text
기관 담당자
-> POST /api/families/links
   -> parent_child_links 저장
```

내부 테이블명은 `parent_child_links`를 유지한다. 서비스 의미는 기관 담당자와 청년 참여자의 연결이다.

## 4. 정책 미션 생성과 제출

```text
기관 담당자
-> POST /api/missions
   -> reward_tasks CREATED 저장

청년 참여자
-> PATCH /api/missions/{id}/submit
   -> reward_tasks SUBMITTED 변경

기관 담당자
-> PATCH /api/missions/{id}/approve
   -> reward_tasks APPROVED 변경
```

반려 시 `REJECTED`로 변경하고, 청년 참여자는 같은 submit API로 보완 내용을 재제출한다.

## 5. 지원금 지급

```text
기관 담당자
-> POST /api/missions/{id}/pay
   -> reward-service가 transfer-service 호출
      Idempotency-Key = reward-payment-{missionId}
   -> transfer-service가 기관 지갑 debit
   -> transfer-service가 청년 지갑 credit
   -> transfers SUCCEEDED
   -> outbox_events PENDING 생성
   -> Kafka transfer.completed 발행
   -> ledger-service가 원장 기록
```

지원금 지급은 일반 송금 기능을 재사용한다. `reward-service`는 지갑 DB를 직접 수정하지 않는다.

## 6. Transfer Failure

```text
출금 전 실패
-> transfers FAILED
-> transfer.failed 이벤트 기록

출금 후 입금 실패
-> transfers COMPENSATION_REQUIRED
-> 운영자가 /api/transfers/compensations/{id}/refund 호출
-> sender wallet 환급
-> transfers COMPENSATED
```

## 7. Ledger Flow

```text
transfer-service
-> outbox_events
-> Kafka transfer.completed
-> ledger-service
-> ledger_entries + ledger_lines 저장
```

원장은 정책 지원금 지급 근거와 장애 추적 근거로 사용한다.

## 8. User Journey

```text
1. 기관 담당자와 청년 참여자가 회원가입한다.
2. 각 사용자에게 지갑이 생성된다.
3. 기관 담당자가 계좌를 연결하고 지원금 예산을 충전한다.
4. 기관 담당자가 청년 참여자를 연결한다.
5. 기관 담당자가 정책 미션을 등록한다.
6. 청년 참여자가 완료 내용을 제출한다.
7. 기관 담당자가 승인 또는 반려한다.
8. 승인된 미션은 지원금 지급 API를 통해 청년 지갑으로 송금된다.
9. 지갑 거래 내역과 원장 기록으로 결과를 확인한다.
```
