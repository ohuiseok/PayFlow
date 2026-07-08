# 블로그 시리즈 목차

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

PayFlow를 블로그 시리즈로 올릴 때 사용할 요약 목차입니다.
상세 발행 순서와 글별 소재는 `17-블로그-발행-로드맵.md`를 기준으로 합니다.

## 시리즈 제목

MSA 기반 지갑/결제 시스템 PayFlow 만들기

## 1편. 프로젝트 시작과 문제 정의

핵심 메시지:

- 단순 CRUD보다 상태, 정합성, 실패 복구를 보여주고 싶었다.
- 청년 정책 미션 지원금이라는 작은 도메인으로 돈의 이동을 모델링했다.

포함할 내용:

- 서비스 한 줄 소개
- 사용자 시나리오
- MVP 범위와 제외한 것
- MVP 범위와 제외한 기능
- 화면 흐름

## 2편. 아키텍처와 서비스 경계

핵심 메시지:

- MSA는 서비스를 많이 쪼개는 것이 아니라 책임과 데이터 소유권을 나누는 것이다.

포함할 내용:

- user, wallet, banking, transfer, reward, ledger 역할
- 서비스별 DB
- cross-service join을 피한 이유
- 내부 API와 Kafka 이벤트의 역할 분리

## 3편. 데이터 모델과 API 계약

핵심 메시지:

- 데이터 모델은 기능 목록이 아니라 도메인 규칙을 담아야 한다.

포함할 내용:

- 서비스별 DB
- ERD
- Gateway route
- 외부 API와 내부 API
- 상태값과 에러 응답

## 4편. 지갑 잔액 정합성 설계

핵심 메시지:

- 돈의 진실은 wallet-service 하나만 가진다.

포함할 내용:

- wallet balance와 wallet_transactions
- DB transaction
- `referenceType` + `referenceId`
- 중복 입금/출금 방어
- 금액 타입 결정

## 5편. 멱등성: 같은 요청이 두 번 와도 돈은 한 번만 움직인다

핵심 메시지:

- API 요청 멱등성과 지갑 반영 멱등성은 다른 층위의 문제다.

포함할 내용:

- `Idempotency-Key`
- `requestHash`
- 같은 key + 같은 body
- 같은 key + 다른 body
- wallet reference와의 차이

## 6편. 송금 상태 머신과 보상 트랜잭션

핵심 메시지:

- 분산 환경에서는 실패를 없애는 것보다 복구 가능한 상태로 남기는 것이 중요하다.

포함할 내용:

- `REQUESTED`, `PROCESSING`, `SUCCEEDED`, `FAILED`
- `COMPENSATION_REQUIRED`
- refund API
- 출금 전 실패와 출금 후 실패의 차이

## 7편. Transactional Outbox와 Kafka

핵심 메시지:

- DB 저장과 Kafka 발행 사이의 빈틈을 outbox로 줄였다.

포함할 내용:

- 왜 바로 Kafka publish를 하지 않았는지
- outbox 상태 전이
- retry와 stale processing 복구
- ledger-service 소비와 중복 방어

## 8편. Open Banking 연동에서 배운 점

핵심 메시지:

- 외부 금융 API의 HTTP 성공은 금융 거래 성공과 다르다.

포함할 내용:

- authorization URL, callback, token encryption
- `bank_tran_id`
- result-check
- timeout/unknown 처리
- 권한 없는 API를 business success로 처리하지 않은 이유

## 9편. 프론트엔드와 API 연결

핵심 메시지:

- 화면은 백엔드 상태 모델을 사용자에게 이해 가능한 흐름으로 드러내야 한다.

포함할 내용:

- 기관 담당자 흐름
- 청년 참여자 흐름
- 계좌/충전 흐름
- API adapter 정렬
- Playwright 검증

## 10편. 테스트와 부하 테스트

핵심 메시지:

- 결제성 시스템 테스트는 기능 성공뿐 아니라 중복 방지와 실패 복구를 검증해야 한다.

포함할 내용:

- 서비스 테스트
- 프론트엔드 타입/e2e 검증
- API smoke script
- k6 시나리오
- p95/p99보다 함께 봐야 하는 데이터 정합성 지표

## 11편. 트러블슈팅과 회고

## 12편. Toss PG 일별 정산과 원장 대사

- banking settlement outbox와 `payment.settlement`
- 정산 consumer의 `event_id` 멱등성
- Spring Batch 기준일/chunk 설계
- 승인·취소 집계와 수수료 계산
- `MATCHED`, `MISSING_LEDGER`, `AMOUNT_MISMATCH`
- 현재 한계: DLT, 운영 권한, migration, 모니터링

핵심 메시지:

- 구현 중 마주친 문제와 남은 한계를 솔직하게 구분했다.

포함할 내용:

- outbox 누락 가능성
- 중복 요청
- compensation
- Docker runtime smoke test 미검증
- Flyway/Liquibase 도입
- Testcontainers
- DLQ
- metrics/alert
- 배포 전략
- saga 확장 가능성
