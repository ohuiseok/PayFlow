# 다음 기술 노트

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

## 프론트엔드/API 연동 상태

현재 상태: `sample-react`는 현재 백엔드 MVP API 범위와 맞춰져 있다.

업데이트된 연동 지점:

- 뱅킹 화면은 `GET /api/bank/accounts`, `POST /api/bank/accounts`, `POST /api/bank/deposits`, `GET /api/bank/transfers/{bankingTransferId}`를 사용한다.
- 미션 화면은 `GET /api/missions`의 배열 응답과 submit/approve/reject PATCH 액션을 사용한다.
- 기관 승인 흐름은 `PATCH /api/missions/{missionId}/approve`와 `POST /api/missions/{missionId}/pay`를 모두 호출해 승인 시 실제 지원금 지급까지 이어지게 한다.
- 참여자 연결은 현재 `reward-service` 계약에 맞춰 기관 담당자가 `POST /api/families/links`에 `childUserId`를 보내 직접 연결하는 방식으로 맞췄다.
- 기관 지원금 요약은 `GET /api/cashbook/parent/summary`를 읽는다.
- 사용 내역 항목은 `GET /api/cashbook/children/{childUserId}/entries`에서 백엔드의 `MissionResponse[]` 형태를 읽는다.
- 프론트엔드 검증 스크립트에 `npm run typecheck`, `npm test` 별칭을 포함했다.
- `tsconfig.json`은 생성 산출물(`dist`, `.expo`, `node_modules`)을 제외해 타입 검사와 웹 export 결과가 충돌하지 않도록 했다.

2026-06-18에 확인한 항목:

- `sample-react`: `npm run check`
- `docker compose config --quiet`
- `banking-service`: `gradlew test`
- `reward-service`: `gradlew test`
- `transfer-service`: `gradlew test`
- `ledger-service`: `gradlew test`

아직 확인하지 못한 항목:

- 전체 Docker Compose 런타임 스모크 테스트. 로컬 환경에서 Docker Desktop이 실행 중이 아니었다.

남은 프론트엔드/API 간극:

- 청년 참여자 측 초대 흐름은 아직 UI 개념으로 남아 있다. 현재 백엔드는 기관 담당자가 직접 생성하는 연결만 지원한다.

## 현재 Kafka MSA 상태

현재 상태: transfer-to-ledger와 Toss-to-settlement 흐름이 Kafka 기반이며 각각 트랜잭셔널 아웃박스를 사용한다.

이미 구현된 항목:

- `docker-compose.yml`의 Kafka 컨테이너
- `banking-service`, `transfer-service`, `ledger-service`, `settlement-service`의 Kafka 설정과 의존성
- `transfer-service`는 `transfer.completed`, `transfer.failed`를 `outbox_events`에 저장
- 아웃박스 릴레이는 `PROCESSING` 상태로 이벤트를 선점하고 Kafka에 발행한 뒤 `PUBLISHED`로 표시하며, `FAILED` 이벤트를 재시도
- 아웃박스 릴레이는 `processing-timeout`이 지난 오래된 `PROCESSING` 이벤트를 복구
- `ledger-service`는 `transfer.completed`를 `transferId` 기준으로 멱등 소비
- `ledger-service`는 `transfer.failed`를 `transferId` 기준으로 멱등 소비
- `ledger-service`는 실패 추적 API 제공
  - `GET /api/ledgers/transfer-failures`
  - `GET /api/ledgers/transfer-failures/{transferId}`
- `transfer-service`는 outbox 발행 요약 API 제공
  - `GET /api/transfers/outbox/summary`
- `banking-service`는 Toss `CHARGE`/`CANCEL` 이벤트를 `payment_settlement_outbox`에 저장
- banking 아웃박스 릴레이는 `payment.settlement`를 발행하고, 설정된 한도까지 실패 행을 재시도
- `settlement-service`는 `payment.settlement`를 `eventId` 기준으로 멱등 소비
- 일별 Spring Batch 작업은 각 이벤트를 `ledger-service`와 대사하고 `MATCHED`, `MISSING_LEDGER`, `AMOUNT_MISMATCH`를 저장
- 정산은 Asia/Seoul 기준 매일 01:00에 전일자로 실행되며, `/api/settlements/daily/{businessDate}`로도 실행/조회 가능

현재 송금 흐름:

- API Gateway가 HTTP 요청을 각 서비스로 라우팅한다.
- `transfer-service`는 OpenFeign HTTP 클라이언트로 `wallet-service`를 호출해 지갑 출금/입금을 수행한다.
- 송신자 지갑 자금 이동은 Redis 락 키 `transfer:wallet-lock:{senderWalletId}`로 보호한다.
- `transfer-service`는 같은 DB 트랜잭션 안에서 송금 상태와 아웃박스 이벤트 의도를 함께 저장한다.
- 아웃박스 릴레이가 Kafka 이벤트를 발행한다.
- `ledger-service`는 `transfer.completed`에서 복식부기 원장 행을 기록한다.
- `ledger-service`는 `transfer.failed`에서 실패 송금 추적 행을 기록한다.
- `transfer-service`는 `COMPENSATION_REQUIRED` 송금에 대해 보상 조회/환불 API를 제공한다.

현재 구조는 의도적으로 혼합형이다. 지갑 자금 이동과 원장 조회는 동기 HTTP를 사용하고, 송금/정산 이벤트 전달은 Kafka를 사용한다.

## 남은 기술 작업

권장 다음 작업:

- 아웃박스 지연, 재시도 횟수, stuck 복구 횟수, 발행 실패 횟수에 대한 메트릭과 알림 추가
- 수동 환불 API만으로 부족할 경우 보상 환불 자동화/재시도 흐름 추가
- 아웃박스 최대 재시도 횟수를 초과한 이벤트에 대한 DLQ 전략 추가
- Testcontainers로 실제 Kafka/Redis 기반 통합 테스트 추가
- settlement consumer의 DLT/오류 처리와 `payment_settlement_outbox` 지연/재시도 고갈 모니터링 추가
- 수동 정산 실행 권한을 운영자 또는 관리자 역할로 제한
- settlement 스키마 관리를 Hibernate update와 임시 SQL 초기화에서 단일 마이그레이션 전략으로 이동
- 지갑 자금 이동을 동기 HTTP로 유지할지, Kafka 기반 saga로 확장할지 결정
