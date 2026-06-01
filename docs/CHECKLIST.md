# PayFlow Implementation Checklist

구현할 때마다 이 파일을 먼저 열고 진행 상황을 체크한다.

체크 기준:

```text
[ ] 아직 시작하지 않음
[-] 진행 중
[x] 완료
```

## 0. 프로젝트 기본 상태

- [x] MSA 기본 폴더 구조 생성
- [x] Docker Compose 기본 구성
- [x] `.env` / `.env.example` 구성
- [x] Eureka 제거 및 Docker DNS 기반 라우팅 전환
- [x] 각 서비스 최소 Spring Boot 빌드 확인
- [x] 구현 계획 문서 작성

관련 문서:

```text
docs/implementation-plan/00-overview.md
docs/implementation-plan/01-common-rules.md
```

## 1. 공통 규칙

- [x] 공통 예외 구조 정의
- [x] 공통 ErrorCode 규칙 정의
- [x] GlobalExceptionHandler 서비스별 적용
- [x] 공통 응답 포맷 결정
- [ ] 날짜/시간 처리 규칙 적용
- [ ] 금액 BigDecimal 처리 규칙 적용
- [ ] 서비스별 패키지 구조 정리

관련 문서:

```text
docs/implementation-plan/01-common-rules.md
```

## 2. DB와 마이그레이션

- [ ] 서비스별 DB 생성 확인
- [ ] user-service 테이블 설계
- [ ] wallet-service 테이블 설계
- [ ] transfer-service 테이블 설계
- [ ] reward-service 테이블 설계
- [ ] ledger-service 테이블 설계
- [ ] settlement-service 테이블 설계
- [ ] 초기 구현용 `ddl-auto=update` 확인
- [ ] Flyway 도입 여부 결정
- [ ] 최종 단계에서 `ddl-auto=validate` 전환

관련 문서:

```text
docs/implementation-plan/02-database-and-migration.md
```

## 3. User Service

- [x] User 엔티티 구현
- [x] UserStatus enum 구현
- [x] UserRepository 구현
- [x] PasswordEncoder 설정
- [x] JWT 발급 유틸 구현
- [x] 회원가입 API 구현
- [x] 로그인 API 구현
- [x] 사용자 조회 API 구현
- [x] user-service 테스트 작성
- [x] user-service `bootJar` 확인

완료 기준:

```text
회원가입, 로그인, JWT 발급, 사용자 조회가 동작한다.
```

관련 문서:

```text
docs/implementation-plan/03-user-service.md
```

## 4. Wallet Service

- [ ] Wallet 엔티티 구현
- [ ] WalletTransaction 엔티티 구현
- [ ] WalletStatus enum 구현
- [ ] WalletTransactionType enum 구현
- [ ] WalletRepository 구현
- [ ] WalletTransactionRepository 구현
- [ ] 지갑 생성 API 구현
- [ ] 잔액 조회 API 구현
- [ ] 충전 API 구현
- [ ] 차감 API 구현
- [ ] 지갑 소유권 검증 구현
- [ ] reference 기반 멱등성 제약 구현
- [ ] 잔액 변경 이력 저장
- [ ] DB row lock 적용
- [ ] wallet-service 테스트 작성
- [ ] 동시성 테스트 작성
- [ ] wallet-service `bootJar` 확인

완료 기준:

```text
잔액 변경이 원자적으로 처리되고, 잔액이 음수가 되지 않는다.
```

관련 문서:

```text
docs/implementation-plan/04-wallet-service.md
```

## 5. Transfer Service

- [ ] Transfer 엔티티 구현
- [ ] TransferStatus enum 구현
- [ ] IdempotencyKey 엔티티 구현
- [ ] OutboxEvent 엔티티 구현
- [ ] Repository 구현
- [ ] request hash 생성 로직 구현
- [ ] IdempotencyService 구현
- [ ] Wallet Feign Client 구현
- [ ] 송금 요청 API 구현
- [ ] 송금 조회 API 구현
- [ ] sender wallet 소유권 검증 구현
- [ ] 송금 상태 전이 구현
- [ ] COMPENSATION_REQUIRED 상태 구현
- [ ] PROCESSING stale 복구 정책 구현 또는 문서화
- [ ] 실패 상태 기록 구현
- [ ] transfer-service 테스트 작성
- [ ] transfer-service `bootJar` 확인

완료 기준:

```text
송금 요청이 상태 기반으로 처리되고, 동일 Idempotency-Key 요청이 중복 차감되지 않는다.
```

관련 문서:

```text
docs/implementation-plan/05-transfer-service.md
```

## 6. Reward Service

- [ ] reward-service 프로젝트 생성
- [ ] payflow_reward DB 설정 확인
- [ ] RewardTask 엔티티 구현
- [ ] RewardTaskStatus enum 구현
- [ ] RewardTaskRepository 구현
- [ ] TransferClient 구현
- [ ] 부모 미션 등록 API 구현
- [ ] 아이 미션 목록/상세 조회 API 구현
- [ ] 아이 완료 요청 API 구현
- [ ] 부모 승인 API 구현
- [ ] 부모 거절 API 구현
- [ ] 승인 시 transfer-service 송금 연동
- [ ] `reward-payment-{taskId}` Idempotency-Key 적용
- [ ] 이미 PAID인 미션 재승인 시 기존 결과 반환
- [ ] 송금 실패 시 failureReason 저장
- [ ] 월별 캘린더 조회 API 구현
- [ ] 월별 지급 완료 금액 합계 구현
- [ ] Gateway reward-service route 추가
- [ ] docker-compose reward-service 추가
- [ ] reward-service 테스트 작성
- [ ] reward-service `bootJar` 확인

완료 기준:

```text
부모가 등록한 일을 아이가 완료 요청하고, 부모 승인 후 부모 지갑에서 아이 지갑으로 실제 용돈이 한 번만 지급된다.
지급 완료된 미션은 아이 캘린더에 날짜별 기록과 월별 합계로 표시된다.
```

관련 문서:

```text
docs/implementation-plan/06-reward-service.md
```

## 7. Open Banking Service

- [x] banking-service 프로젝트 생성
- [ ] payflow_banking DB 설정 확인
- [ ] BankAccount 엔티티 구현
- [ ] BankingTransfer 엔티티 구현
- [ ] BankingApiLog 엔티티 구현
- [ ] BankAccountStatus enum 구현
- [ ] BankingTransferStatus enum 구현
- [ ] Repository 구현
- [ ] OpenBankingClient 인터페이스 구현
- [ ] MockOpenBankingClient 구현
- [ ] MockOpenBankingClient 성공 응답 시뮬레이션
- [ ] MockOpenBankingClient 명시 실패 응답 시뮬레이션
- [ ] MockOpenBankingClient timeout 응답 시뮬레이션
- [ ] MockOpenBankingClient 처리 중 응답 시뮬레이션
- [ ] MockOpenBankingClient bank_tran_id 중복 응답 시뮬레이션
- [ ] KftcOpenBankingClient 테스트베드 profile 문서화
- [ ] Wallet Feign Client 구현
- [ ] 계좌 등록 API 구현
- [ ] 충전 요청 API 구현
- [ ] 오픈뱅킹 출금이체 성공 후 wallet-service deposit 연동
- [ ] bank_tran_id/api_tran_id 저장
- [ ] bank_tran_id unique 제약 구현
- [ ] wallet referenceType/referenceId 기반 중복 반영 방어 확인
- [ ] Idempotency-Key 검증 구현
- [ ] request hash 생성 로직 구현
- [ ] 같은 Idempotency-Key + 같은 body 기존 결과 반환
- [ ] 같은 Idempotency-Key + 다른 body 409 반환
- [ ] 오픈뱅킹 실패 상태 저장
- [ ] 응답 불명 UNKNOWN 상태 저장
- [ ] 처리 중 BANK_PROCESSING 상태 저장
- [ ] 이체결과조회 API client 메서드 구현
- [ ] UNKNOWN/BANK_PROCESSING 결과조회 워커 구현
- [ ] 결과조회 성공 시 최종 상태 갱신
- [ ] 결과조회 실패 시 FAILED 또는 COMPENSATION_REQUIRED 전이
- [ ] 출금/환불 상태 모델 구현 또는 문서화
- [ ] 출금 API 최소 구현
- [ ] 출금 입금이체 실패 시 보상 근거 저장
- [ ] 정보제공자 API는 2차 범위로 문서화
- [ ] banking-service 테스트 작성
- [ ] banking-service `bootJar` 확인

완료 기준:

```text
MockOpenBankingClient로 충전 성공/실패/불명/처리중/중복 시나리오를 검증한다.
외부 은행망 성공이 확정된 충전만 wallet-service 잔액에 반영되고, bank_tran_id와 wallet reference 기준으로 중복 반영되지 않는다.
UNKNOWN/BANK_PROCESSING 상태는 결과조회 워커로 최종 상태를 확정할 수 있다.
출금은 최소 API와 보상 근거까지 남기고, 정보제공자 API는 2차 범위로 남긴다.
```

관련 문서:

```text
docs/implementation-plan/07-open-banking-service.md
```

## 8. Redis Lock

- [ ] RedisLockManager 구현
- [ ] 락 key 규칙 구현
- [ ] 두 지갑 락 정렬 규칙 구현
- [ ] Lua 기반 안전한 unlock 구현
- [ ] 락 획득 실패 예외 처리
- [ ] transfer-service 송금 흐름에 락 적용
- [ ] 락 단위 테스트 작성
- [ ] 동시 송금 테스트 작성

완료 기준:

```text
같은 지갑에 대한 동시 송금에서도 잔액 정합성이 깨지지 않는다.
```

관련 문서:

```text
docs/implementation-plan/08-redis-lock.md
```

## 9. Outbox And Kafka

- [ ] OutboxEvent 저장 로직 구현
- [ ] TransferCompleted payload 정의
- [ ] TransferFailed payload 정의
- [ ] Kafka Producer 설정
- [ ] OutboxEventPublisher 구현
- [ ] OutboxEventScheduler 구현
- [ ] READY -> PUBLISHING 선점 처리
- [ ] 발행 성공 시 PUBLISHED 처리
- [ ] 발행 실패 시 retryCount 증가
- [ ] stale PUBLISHING 복구 처리
- [ ] Outbox 테스트 작성
- [ ] Kafka 이벤트 발행 테스트 작성

완료 기준:

```text
송금 성공 후 OutboxEvent가 저장되고, Kafka 발행 성공 시 PUBLISHED 상태가 된다.
```

관련 문서:

```text
docs/implementation-plan/09-outbox-and-kafka.md
```

## 10. Ledger Service

- [ ] LedgerEntry 엔티티 구현
- [ ] LedgerLine 엔티티 구현
- [ ] ProcessedEvent 엔티티 구현
- [ ] Repository 구현
- [ ] transfer.completed Kafka Consumer 구현
- [ ] eventId 중복 처리 구현
- [ ] 원장 라인 2개 생성 구현
- [ ] 송금별 원장 조회 API 구현
- [ ] ledger-service 테스트 작성
- [ ] ledger-service `bootJar` 확인

완료 기준:

```text
송금 완료 이벤트 1건당 원장 기록 1건과 원장 라인 2건이 생성된다.
```

관련 문서:

```text
docs/implementation-plan/10-ledger-service.md
```

## 11. Settlement Service

- [ ] SettlementTarget 엔티티 구현
- [ ] SettlementDay 엔티티 구현
- [ ] SettlementItem 엔티티 구현
- [ ] 수수료 계산기 구현
- [ ] transfer.completed 이벤트 소비 구현
- [ ] 정산 후보 저장 구현
- [ ] dailySettlementJob 구현
- [ ] 정산 실행 API 구현
- [ ] 정산 조회 API 구현
- [ ] settlement-service 테스트 작성
- [ ] settlement-service `bootJar` 확인

완료 기준:

```text
일별 거래를 집계하고 수수료를 계산한 정산 결과를 저장한다.
```

관련 문서:

```text
docs/implementation-plan/11-settlement-service.md
```

## 12. Gateway And Security

- [x] Gateway route 확인
- [x] 인증 제외 경로 정의
- [x] JWT 인증 필터 구현
- [x] JWT 검증 실패 처리
- [x] 외부 `X-User-*` 헤더 제거
- [x] 내부 서비스로 `X-User-Id` 전달
- [x] `X-User-Email` 전달
- [x] 소유권 실패 403 처리
- [x] Gateway 테스트 작성
- [x] api-gateway `bootJar` 확인

완료 기준:

```text
인증이 필요한 API는 Gateway에서 JWT를 검증하고 내부 서비스로 사용자 정보를 전달한다.
```

관련 문서:

```text
docs/implementation-plan/12-gateway-and-security.md
```

## 13. 장애 복구

- [ ] 동일 Idempotency-Key 반복 요청 테스트
- [ ] 같은 지갑 동시 송금 테스트
- [ ] wallet-service 장애 테스트
- [ ] sender 차감 성공 후 receiver 증가 실패 시나리오 문서화
- [ ] COMPENSATION_REQUIRED/ROLLBACK_FAILED 처리 기준 확인
- [ ] Outbox 발행 실패 테스트
- [ ] Outbox PUBLISHING stale 복구 테스트
- [ ] Kafka 중복 이벤트 테스트
- [ ] PROCESSING 상태 복구 전략 문서화
- [ ] 실패 상태와 실패 원인 저장 확인

완료 기준:

```text
주요 장애 시나리오에서 데이터가 조용히 유실되지 않고, 실패 상태와 재처리 근거가 남는다.
```

관련 문서:

```text
docs/implementation-plan/13-failure-recovery.md
```

## 14. 테스트

- [ ] user-service 단위/통합 테스트
- [ ] wallet-service 단위/통합 테스트
- [ ] transfer-service 단위/통합 테스트
- [ ] reward-service 단위/통합 테스트
- [ ] ledger-service 단위/통합 테스트
- [ ] settlement-service 단위/통합 테스트
- [ ] Redis lock 테스트
- [ ] Outbox 테스트
- [ ] Kafka consumer 멱등성 테스트
- [ ] 동시성 테스트
- [ ] 전체 smoke test

완료 기준:

```text
정상 흐름보다 중복 요청, 동시성, 장애, 재처리 테스트가 우선되어야 한다.
```

관련 문서:

```text
docs/implementation-plan/14-testing-strategy.md
```

## 15. 로컬 실행과 배포

- [ ] 로컬 MySQL 사용 방식 확인
- [ ] Docker MySQL 3307 포트 실행 방식 확인
- [ ] Redis/Kafka만 실행하는 개발 방식 확인
- [ ] 전체 Docker Compose 실행 확인
- [ ] settlement profile 실행 확인
- [ ] EC2 t3.medium 메모리 기준 확인
- [ ] swap 2GB 설정 문서화
- [ ] GitHub Actions Secrets 목록 정리
- [ ] 배포 스크립트 작성
- [ ] 배포 후 health check 작성

완료 기준:

```text
로컬과 EC2에서 같은 `.env` 기반 방식으로 실행할 수 있다.
```

관련 문서:

```text
docs/implementation-plan/15-local-and-deploy.md
```

## 16. 최종 포트폴리오 정리

- [ ] README 최신화
- [ ] 아키텍처 다이어그램 정리
- [ ] 핵심 장애 시나리오 정리
- [ ] 테스트 결과 정리
- [ ] 메모리 제약과 설계 판단 정리
- [ ] 결제 정합성 설계 설명 추가
- [ ] Outbox와 원장 설계 설명 추가
- [ ] 정산 흐름 설명 추가
- [ ] 용돈 캘린더 공모전 시나리오 설명 추가

완료 기준:

```text
README만 읽어도 결제 회사 면접에서 설명할 수 있는 설계 의도가 보여야 한다.
```

## 매 작업 완료 시 체크

- [x] 문서 전용 작업이면 빌드 생략 사유 확인
- [x] 코드 변경 작업이면 관련 서비스 `bootJar` 성공
- [x] 관련 테스트 성공
- [x] `docker compose config --quiet` 성공
- [ ] 문서와 실제 구현 불일치 여부 확인
- [x] CHECKLIST.md 업데이트
