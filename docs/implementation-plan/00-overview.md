# 00. Implementation Overview

## Code Comment Rule

코드 구현 시에는 주석을 반드시 작성한다. 특히 결제/지갑 정합성, 멱등성, 트랜잭션, 락, JWT/보안 헤더, 이벤트 발행, 보상 처리처럼 원리를 이해해야 하는 부분은 `docs/code-commenting-guide.md` 기준에 따라 왜 필요한지까지 상세히 남긴다.

PayFlow는 포트폴리오용 결제 MVP다.

핵심 목표는 "사용자 지갑, 충전, 송금, 가족 미션 보상, 원장 기록"이 실제로 작동하고 테스트로 설명되는 것이다.

## MVP 서비스

api-gateway

user-service

wallet-service

banking-service

transfer-service

reward-service

ledger-service

## 인프라

MySQL

Nginx

Docker Compose

## 설계 원칙

1. 하나의 기능은 끝까지 동작해야 한다.
2. 테이블은 현재 API와 서비스 흐름에서 실제로 쓰는 것만 둔다.
3. 멱등성은 거래 테이블의 `idempotency_key`, `request_hash`로 처리한다.
4. 가족은 별도 family aggregate 없이 `parent_child_links`로 단순하게 표현한다.
5. 미션 제출 이력은 `reward_tasks` 상태 전이로 표현한다.
6. 캐시북은 별도 테이블 없이 지갑 거래와 지급 완료 미션으로 조회한다.
7. 원장은 `ledger_entries`, `ledger_lines`로 복식부기 형태를 유지한다.
8. 서비스 간 호출은 동기 HTTP로 시작한다.
9. 실패 복구는 상태값과 재시도 가능한 API로 설명한다.

## 구현 순서

1. 공통 규칙과 에러 응답
2. DB 스키마와 마이그레이션
3. user-service
4. wallet-service
5. banking-service
6. transfer-service
7. reward-service
8. ledger-service
9. api-gateway
10. 통합 테스트와 문서 검증

## 완료 기준

회원 가입 후 지갑이 생성된다.

은행 계좌를 연결하고 충전할 수 있다.

사용자 간 송금이 가능하다.

부모가 자녀에게 미션을 만들고 보상을 지급할 수 있다.

송금과 보상 지급이 원장에 기록된다.

동일 요청 재시도 시 중복 충전, 중복 송금, 중복 보상이 발생하지 않는다.

주요 실패 케이스가 상태값과 테스트로 설명된다.
## 결제 수단 확장 계획

기존 MVP는 `banking-service`의 계좌 기반 충전 흐름을 중심으로 한다. 다음 단계에서는 같은 충전 도메인 안에 Toss Payments PG 충전과 Open Banking 계좌 연결을 추가한다.

- Toss 충전은 별도 `payment_charges`, `toss_payment_orders`, `toss_payment_events` 테이블로 외부 결제 상태를 추적한다.
- Open Banking 계좌 연결은 `open_banking_authorizations` 테이블과 확장된 `bank_accounts`로 인증/계좌 동기화 상태를 저장한다.
- 지갑 잔액 변경은 계속 `wallet-service`의 내부 입금 API만 사용한다.
- 화면은 부모 홈의 `Toss 충전` 버튼과 `Open Banking 계좌 연결` 버튼을 기준으로 재구성한다.
- 상세 계획은 `docs/implementation-plan/08-toss-pg-extension.md`를 따른다.
