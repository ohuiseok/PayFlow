# 00. Implementation Overview

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
