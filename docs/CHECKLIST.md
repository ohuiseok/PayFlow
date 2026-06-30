# PayFlow Implementation Checklist

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

## Current Verified Status - 2026-06-18

- [x] Backend service tests passed for `user-service`, `wallet-service`, `banking-service`, `transfer-service`, `reward-service`, and `ledger-service`.
- [x] `docker compose config --quiet` passed.
- [x] `sample-react` API adapters are aligned with the current MVP backend routes.
- [x] `sample-react` `npm run check` passed, including type check, web export, dummy Playwright e2e, and mocked API-mode Playwright e2e.
- [x] Agency mission approval screen now queries real mission data and calls approve plus pay APIs.
- [x] Agency family linking screen now matches the current direct-link backend contract.
- [x] Dedicated parent credit summary backend endpoint is implemented and connected from the frontend.
- [ ] Full Docker Compose runtime smoke test remains pending because Docker Desktop was not running locally.

구현 대상만 정리한 체크리스트입니다.

체크 기준:

```text
[ ] 아직 시작하지 않음
[-] 진행 중
[x] 완료
```

## 현재 우선순위

```text
1. user/wallet/gateway 기반 확인
2. transfer-service 송금과 멱등성 구현
3. ledger-service 내부 원장 기록 구현
4. banking-service 충전/출금 구현
5. reward-service 기관-청년 참여자 연결과 정책 미션 지원금 지급 구현
6. smoke test와 문서 정리
```

## 0. 프로젝트 기본 상태

- [x] MSA 기본 디렉터리 구조 생성
- [x] Docker Compose 기본 구성
- [x] `.env` / `.env.example` 구성
- [x] Docker DNS 기반 라우팅 전환
- [x] 각 서비스 최소 Spring Boot 빌드 확인
- [x] 구현 계획 문서 작성

## 1. DB와 마이그레이션

- [x] 서비스별 DB 생성 스크립트 확인
- [x] user-service 테이블 설계
- [x] wallet-service 테이블 설계
- [x] transfer-service 테이블 설계
- [x] banking-service 테이블 설계
- [x] reward-service 테이블 설계
- [x] ledger-service 테이블 설계
- [x] 초기 구현용 `ddl-auto=update` 확인

관련 문서:

```text
docs/erd.md
docs/implementation-plan/02-database-and-migration.md
```

## 2. User Service

- [x] User 엔티티 구현
- [x] UserStatus enum 구현
- [x] UserRole enum 구현
- [x] UserRepository 구현
- [x] PasswordEncoder 설정
- [x] JWT 발급 유틸 구현
- [x] 회원가입 API 구현
- [x] 로그인 API 구현
- [x] 사용자 조회 API 구현
- [x] JWT role claim 반영
- [x] user-service 테스트 작성
- [x] user-service `bootJar` 확인

## 3. Wallet Service

- [x] Wallet 엔티티 구현
- [x] WalletTransaction 엔티티 구현
- [x] WalletStatus enum 구현
- [x] WalletTransactionType enum 구현
- [x] WalletRepository 구현
- [x] WalletTransactionRepository 구현
- [x] 지갑 생성 API 구현
- [x] 잔액 조회 API 구현
- [x] 입금 API 구현
- [x] 출금 API 구현
- [x] 지갑 소유권 검증 구현
- [x] reference 기반 중복 반영 방어 구현
- [x] 잔액 변경 이력 저장
- [x] DB row lock 적용
- [x] wallet-service 테스트 작성
- [x] 동시성 테스트 작성
- [x] wallet-service `bootJar` 확인

완료 기준:

```text
잔액 변경이 원자적으로 처리되고, 같은 reference 요청이 중복 반영되지 않는다.
```

## 4. Transfer Service

- [x] Transfer 엔티티 구현
- [x] TransferStatus enum 구현
- [x] `idempotencyKey` unique 제약 구현
- [x] `requestHash` 저장 구현
- [x] TransferRepository 구현
- [x] request hash 생성 로직 구현
- [x] Wallet Feign Client 구현
- [x] Ledger 원장 기록: Kafka Outbox 패턴으로 비동기 발행 (Feign 대신 이벤트 발행)
- [x] wallet-service 내부 지갑 조회 API 계약 적용
- [x] 송금 wallet referenceId 규칙 적용
- [x] 송금 요청 API 구현
- [x] 송금 조회 API 구현
- [x] sender wallet 소유권 검증 구현
- [x] 송금 상태 전이 구현
- [x] COMPENSATION_REQUIRED 상태 구현
- [x] 실패 상태 기록 구현
- [x] 송금 완료 후 ledger-service 원장 기록 호출 (Kafka Outbox)
- [x] transfer-service 테스트 작성
- [x] transfer-service `bootJar` 확인

완료 기준:

```text
송금 요청이 상태 기반으로 처리되고, 동일 Idempotency-Key 요청이 중복 차감되지 않는다.
```

## 5. Ledger Service

- [x] LedgerEntry 엔티티 구현
- [x] LedgerLine 엔티티 구현
- [x] Repository 구현
- [x] 내부 원장 기록 API 구현 (Kafka consumer 기반)
- [x] transferId unique 기반 중복 원장 방지
- [x] 원장 라인 2건 생성 구현 (차변/대변)
- [x] 송금별 원장 조회 API 구현
- [x] ledger-service 테스트 작성
- [x] ledger-service `bootJar` 확인

완료 기준:

```text
송금 1건당 원장 헤더 1건과 원장 라인 2건이 생성된다.
```

## 6. Banking Service

- [x] banking-service 프로젝트 생성
- [x] payflow_banking DB 설정 확인
- [x] BankAccount 엔티티 구현
- [x] BankingTransfer 엔티티 구현
- [x] BankAccountStatus enum 구현
- [x] BankingTransferStatus enum 구현
- [x] Repository 구현
- [x] OpenBankingClient 구현 (Mock/Real 구현체 분리)
- [x] Wallet Feign Client 구현
- [x] 연결 계좌 목록 API 구현
- [x] 연결 계좌 등록 API 구현
- [-] 연결 계좌 삭제 API 구현 (OpenBanking 연동 해제는 별도)
- [x] 충전 요청 API 구현 (OpenBanking + Toss PG)
- [x] 충전 결과 조회 API 구현
- [x] 충전 성공 후 wallet-service deposit 연동
- [x] 출금 요청 API 구현
- [x] 출금 결과 조회 API 구현
- [x] 출금 시 wallet-service withdraw 연동
- [x] bank_tran_id unique 제약 구현 (BankingTransfer.idempotencyKey)
- [x] wallet referenceId 기반 중복 반영 방어 확인
- [x] Idempotency-Key + requestHash 구현
- [x] 같은 Idempotency-Key + 같은 body 기존 결과 반환
- [x] 같은 Idempotency-Key + 다른 body 409 반환
- [x] 실패/UNKNOWN 상태 저장
- [x] banking-service 테스트 작성
- [x] banking-service `bootJar` 확인

## 7. Reward Service

- [x] reward-service 프로젝트 생성
- [x] payflow_reward DB 설정 확인
- [x] AgencyYouthLink 엔티티 구현
- [x] RewardTask 엔티티 구현
- [x] AgencyYouthLinkStatus enum 구현
- [x] RewardTaskStatus enum 구현
- [x] Repository 구현
- [x] WalletClient 구현
- [x] TransferClient 구현
- [x] 기관-청년 참여자 연결 생성 API 구현
- [x] 참여자 목록 조회 API 구현 (children/parents 분리)
- [-] 참여자 연결 해제 API 구현 (FamilyController에 미포함, 추후 추가)
- [x] 기관 정책 미션 등록 API 구현
- [-] 미션 수정 API 구현 (미포함, 상태 전이로 대체)
- [x] 미션 취소 API 구현 (approve/reject/pay 상태 전이)
- [x] 미션 목록/상세 조회 API 구현
- [-] 월별 미션 캘린더 API 구현 (미포함, 목록 API로 대체)
- [x] 청년 완료 제출 API 구현
- [-] 청년 재제출 API 구현 (미포함, 추후 추가)
- [x] 기관 승인 API 구현
- [x] 기관 반려 API 구현
- [x] 승인 시 transfer-service 송금 연동
- [x] `reward-payment-{missionId}` Idempotency-Key 적용
- [x] 이미 PAID인 미션 재승인 시 기존 결과 반환
- [x] 송금 실패 시 failureReason 저장
- [x] 사용 기록 요약 API 구현
- [x] 사용 기록 내역 API 구현
- [x] Gateway reward-service route 추가 (/api/families/**, /api/missions/**, /api/cashbook/**)
- [x] docker-compose reward-service 추가
- [x] reward-service 테스트 작성
- [x] reward-service `bootJar` 확인

완료 기준:

```text
기관 담당자가 등록한 미션을 청년이 제출하고, 기관 승인 후 기관 지갑에서 청년 지갑으로 실제 보상이 한 번만 지급된다.
```

## 8. Gateway And Security

- [x] Gateway route 확인
- [x] 인증 제외 경로 정의
- [x] JWT 인증 필터 구현
- [x] JWT 검증 실패 처리
- [x] 외부 `X-User-*` 헤더 제거
- [x] 외부 `X-Internal-*` 헤더 제거
- [x] 내부 서비스로 `X-User-Id` 전달
- [x] 내부 서비스로 `X-User-Phone-Number` 전달
- [x] api-gateway 테스트 작성
- [x] api-gateway `bootJar` 확인
- [x] `/api/bank/**` route 추가 또는 확인
- [x] `/api/families/**` route 추가
- [x] `/api/missions/**` route 추가
- [x] `/api/cashbook/**` route 추가

## 9. 테스트와 실행

- [x] user-service 단위/통합 테스트
- [x] wallet-service 단위/통합 테스트
- [x] transfer-service 단위/통합 테스트 (TransferServiceTest, OutboxEventRelayTest, TransferCompensationControllerTest 등)
- [x] banking-service 단위/통합 테스트 (BankingServiceTest, TossPaymentServiceTest, BankingControllerTest)
- [x] reward-service 단위/통합 테스트 (RewardServiceTest, MissionControllerTest, FamilyControllerTest)
- [x] ledger-service 단위/통합 테스트 (LedgerServiceTest, LedgerControllerTest, TransferEventConsumerTest)
- [x] k6 E2E 시나리오 스크립트 작성 (`k6/e2e-scenario.js`)
- [-] 전체 smoke test (Docker Desktop 미실행 환경에서 보류)
- [-] `docker compose config --quiet` (Docker Desktop 미실행 환경에서 보류)
- [-] 전체 Docker Compose 실행 확인 (Docker Desktop 미실행 환경에서 보류)
- [x] README 최신화 (2026-06-20)

## 마지막 작업 완료 전 체크

- [x] 문서와 실제 구현 범위 불일치 확인 (2026-06-20)
- [-] 관련 서비스 `bootJar` 성공 (Docker 미실행 환경에서 보류)
- [x] 관련 테스트 코드 작성 완료 (2026-06-20)
- [x] 체크리스트 갱신 (2026-06-20)

## 10. 보안 및 코드 품질 개선 (2026-06-20 코드 리뷰)

상세 내용: [`docs/security-review.md`](security-review.md)  
개발 계획: [`docs/implementation-plan/17-security-and-improvements.md`](implementation-plan/17-security-and-improvements.md)

### Phase 1 — Critical (즉시 수정)

- [x] **[C-1]** GatewayRequestFilter: 시크릿 미설정 시 `return false` 처리 (전체 7개 서비스)
- [x] **[C-2]** 내부 시크릿 비교: `String.equals()` → `MessageDigest.isEqual()` 교체 (전체 서비스)
- [x] **[C-3]** `.env` MySQL 패스워드 강화 및 시크릿 점검
- [x] **[C-4]** LedgerController: `getLedgerEntries`, `getTransferFailures`에 `X-User-Id` 필터 추가
- [x] **[C-5]** TossPaymentController: 운영 API(`/operations/*`)에 역할 검증 추가
- [x] **[C-6]** RewardService.payMission: `findByIdForUpdate` (PESSIMISTIC_WRITE) 적용

### Phase 2 — High (다음 스프린트)

- [ ] **[H-1]** user, wallet, transfer, reward, settlement 서비스: `ddl-auto=validate` + Flyway 전환 (`settlement-service`는 현재 `ddl-auto=update`와 `batch-schema.sql` 사용)
- [x] **[H-2]** UserService.createUser: 지갑 생성 `@Retryable` + `WalletProvisioningService` 재시도 보상 로직 추가
- [x] **[H-3]** TokenCryptoService: 암호화 키 미설정 시 `@PostConstruct`에서 기동 실패 처리
- [x] **[H-4]** CreateUserRequest: 기관 담당자 역할 자가 선택 제한 (`inviteCode` 기반, `MessageDigest.isEqual()` 상수 시간 비교)
- [x] **[H-5]** Kafka DLT 설정: `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` 추가 (ledger-service)
- [x] **[H-6]** TossPaymentService.cancel: `SELECT FOR UPDATE` 비관적 락 추가

### Phase 3 — Medium (코드 품질)

- [x] **[M-1]** 전체 서비스 `GlobalExceptionHandler`에 `@Slf4j` + 예외 로깅 추가
- [x] **[M-2]** 로그인 브루트포스 방어: nginx `limit_req_zone` (5r/m, burst=3) rate limiting 추가
- [x] **[M-3]** Transfer 조회: `senderUserId` / `receiverUserId` 인덱스 추가 + `Pageable` 페이지네이션 도입
- [x] **[M-4]** RewardService.getAgencyCreditSummary: 전체 목록 로드 → `COUNT` / `SUM` 쿼리 교체
- [x] **[M-5]** JwtAuthenticationFilter: 수동 JSON 직렬화 → `ObjectMapper` 주입으로 교체
- [x] **[M-6]** PROCESSING 상태 고착 Transfer 감지: `StuckTransferMonitor` 스케줄러 추가 (5분 주기)
- [x] **[M-7]** TokenCryptoService: SHA-256 단순 해시 → PBKDF2WithHmacSHA256 키 유도 함수로 교체 + 레거시 SHA-256 fallback 복호화
- [x] **[M-8]** OutboxEventRelay: ShedLock + MySQL JDBC 분산 스케줄러 락 도입 (`V2__add_shedlock.sql`)

# Recent implementation status

- [x] transfer-service Redis sender wallet lock
- [x] transfer-service transactional outbox table and publisher relay
- [x] outbox PROCESSING claim and retry limit
- [x] outbox stale PROCESSING recovery
- [x] ledger-service idempotent `transfer.completed` consumer verification
- [x] ledger-service `transfer.failed` consumer and `transfer_failure_events` persistence
- [x] ledger-service transfer failure lookup APIs
  - `GET /api/ledgers/transfer-failures`
  - `GET /api/ledgers/transfer-failures/{transferId}`
- [x] documentation updated for Kafka/outbox/failure tracking flow
- [x] banking-service Toss 승인/취소 `payment_settlement_outbox` 저장
- [x] banking outbox relay의 `payment.settlement` 발행과 제한 재시도
- [x] settlement-service `event_id` 기반 멱등 소비
- [x] Spring Batch 기준일별 집계와 원장 대사
- [x] 정산 수동 실행/조회 API
- [x] 정산 배치 통합 테스트
- [ ] settlement consumer DLT와 독성 메시지 격리
- [ ] banking settlement outbox 적체/재시도 소진 모니터링 API
- [ ] 정산 수동 실행 API 관리자 역할 제한
- [ ] settlement 스키마를 Flyway로 단일화


