# 17. 보안 및 코드 품질 개선 계획

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

**작성일**: 2026-06-20  
**근거 문서**: [`docs/security-review.md`](../security-review.md)

코드 리뷰 결과를 바탕으로 수정 우선순위와 구현 방향을 정리합니다.

---

## Phase 1 — Critical (즉시 수정)

Critical 항목은 자금 손실, 인증 우회, 개인정보 유출로 이어질 수 있으므로 다른 작업보다 먼저 처리합니다.

---

### 1-A. GatewayRequestFilter Fail-Open 수정 [C-1]

**영향 서비스**: user, wallet, banking, transfer, reward, ledger, settlement (7개)

각 서비스의 `GatewayRequestFilter.java`에서 시크릿 미설정 시 `true` 반환하는 로직을 `false`로 변경합니다.

```java
// 수정 전
if (!StringUtils.hasText(gatewayInternalSecret) && !StringUtils.hasText(internalSecret)) {
    return true;
}

// 수정 후
if (!StringUtils.hasText(gatewayInternalSecret) && !StringUtils.hasText(internalSecret)) {
    return false;
}
```

수정 후 모든 서비스 테스트를 실행하여 내부 통신이 정상 동작하는지 확인합니다.

---

### 1-B. 내부 시크릿 비교: 상수 시간 비교 적용 [C-2]

**영향 파일**: 모든 서비스 `GatewayRequestFilter.java`, `wallet-service/WalletController.java`

`String.equals()`를 `MessageDigest.isEqual()`로 교체합니다. 유틸 메서드를 추출하여 전 서비스에 동일하게 적용합니다.

```java
private static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) return false;
    return MessageDigest.isEqual(
        a.getBytes(StandardCharsets.UTF_8),
        b.getBytes(StandardCharsets.UTF_8)
    );
}
```

---

### 1-C. LedgerController userId 필터 추가 [C-4]

**영향 파일**: `ledger-service/.../LedgerController.java`, `LedgerService.java`, `LedgerRepository.java`

1. `getLedgerEntries(Long userId)` — `WHERE sender_user_id = ? OR receiver_user_id = ?` 조건 추가
2. `getTransferFailures(Long userId)` — 동일 userId 필터 추가
3. 컨트롤러에서 `@RequestHeader("X-User-Id") Long requestUserId` 추가

---

### 1-D. TossPaymentController 운영 API 접근 제한 [C-5]

**영향 파일**: `banking-service/.../TossPaymentController.java`

운영 API(`/operations/summary`, `/operations/compensations`)에 역할 검증을 추가합니다.

```java
@GetMapping("/operations/summary")
public TossOperationalSummaryResponse getOperationalSummary(
    @RequestHeader("X-User-Role") String role
) {
    if (!"ROLE_PARENT".equals(role) && !"ROLE_ADMIN".equals(role)) {
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    return tossPaymentService.getOperationalSummary();
}
```

장기적으로는 별도 내부 관리 서비스로 분리를 검토합니다.

---

### 1-E. RewardService.payMission 비관적 락 추가 [C-6]

**영향 파일**: `reward-service/.../RewardService.java`, `RewardTaskRepository.java`

```java
// RewardTaskRepository.java 추가
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT t FROM RewardTask t WHERE t.id = :id")
Optional<RewardTask> findByIdForUpdate(@Param("id") Long id);

// RewardService.payMission 수정
RewardTask task = rewardTaskRepository.findByIdForUpdate(missionId)
    .orElseThrow(() -> new BusinessException(ErrorCode.MISSION_NOT_FOUND));
```

---

### 1-F. .env 패스워드 교체 및 시크릿 점검 [C-3]

- `MYSQL_ROOT_PASSWORD`, `MYSQL_PASSWORD` 강도 높은 값으로 교체
- 운영 배포 전 CI/CD 파이프라인에 `.env` 시크릿 검증 단계 추가 검토
- `replace-before-prod` 주석 패턴을 탐지하는 pre-commit hook 또는 CI 검사 추가

---

## Phase 2 — High (다음 스프린트)

---

### 2-A. ddl-auto=update → validate + Flyway 전환 [H-1]

**영향 서비스**: user, wallet, transfer, reward, settlement

각 서비스에 Flyway 의존성을 추가하고 초기 스키마 마이그레이션 파일을 생성합니다.

```
서비스/src/main/resources/db/migration/
  V1__init.sql       ← 현재 @Entity 기반 스키마를 DDL로 변환
  V2__add_indexes.sql ← 인덱스 추가 (M-3 대응)
```

`application.yml`에서 `spring.jpa.hibernate.ddl-auto: validate`로 변경.

---

### 2-B. UserService.createUser Outbox 패턴 적용 [H-2]

**영향 파일**: `user-service/.../UserService.java`

사용자 생성과 지갑 생성 요청을 같은 트랜잭션에서 처리하는 방식으로 개선합니다.

1. `user-service`에 `UserCreatedOutbox` 테이블 추가
2. 사용자 저장 + Outbox 이벤트 저장을 한 트랜잭션으로 처리
3. 별도 릴레이가 Outbox 이벤트를 읽어 `walletClient.createWallet()` 호출
4. 성공 시 Outbox 이벤트 삭제

단기 대안: `walletClient.createWallet()` 실패 시 `@Retryable`로 재시도하고 최종 실패 시 별도 보상 배치로 처리.

---

### 2-C. OpenBanking 토큰 암호화 키 배포 안전 장치 [H-3]

**영향 파일**: `banking-service/.../TokenCryptoService.java`

```java
@PostConstruct
void validate() {
    String defaultKey = "payflow-local-token-secret";
    if (!StringUtils.hasText(encryptionSecret) || encryptionSecret.equals(defaultKey)) {
        throw new IllegalStateException(
            "OPENBANKING_TOKEN_ENCRYPTION_SECRET이 설정되지 않았습니다. " +
            "운영 환경에서는 반드시 명시적으로 설정해야 합니다."
        );
    }
}
```

`SPRING_PROFILES_ACTIVE=prod` 조건으로 프로파일별로만 적용할 수도 있습니다.

---

### 2-D. Kafka DLT 및 재시도 설정 [H-5]

**영향 파일**: `ledger-service/.../KafkaConsumerConfig.java` (신규 또는 기존 설정 파일)

```java
@Bean
public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
    FixedBackOff backOff = new FixedBackOff(1000L, 3L);  // 1초 간격 3회
    return new DefaultErrorHandler(recoverer, backOff);
}

@Bean
public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
    ConsumerFactory<String, String> cf,
    DefaultErrorHandler errorHandler
) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(cf);
    factory.setCommonErrorHandler(errorHandler);
    return factory;
}
```

DLT로 전달된 메시지는 별도 모니터링 알림으로 연동합니다.

---

### 2-E. TossPaymentService.cancel 비관적 락 추가 [H-6]

**영향 파일**: `banking-service/.../TossPaymentService.java`, `PaymentChargeRepository.java`

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<PaymentCharge> findByIdAndUserId(Long id, Long userId);
```

---

## Phase 3 — Medium (코드 품질 개선)

---

### 3-A. GlobalExceptionHandler 로깅 추가 [M-1]

모든 서비스의 `GlobalExceptionHandler.java`에 `@Slf4j` 및 예외 로깅 추가.

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception exception) {
        log.error("Unhandled exception", exception);
        return ResponseEntity.status(500).body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
```

---

### 3-B. 로그인 Rate Limiting 추가 [M-2]

**옵션 1**: nginx 레벨 (단기, 간단)

```nginx
limit_req_zone $binary_remote_addr zone=login:10m rate=5r/m;
location /api/users/login {
    limit_req zone=login burst=3 nodelay;
}
```

**옵션 2**: api-gateway Redis Rate Limiter (중기, 정확)

Spring Cloud Gateway의 `RequestRateLimiter` 필터 + Redis 기반 구현.

---

### 3-C. 거래 내역 조회 페이지네이션 및 인덱스 추가 [M-3]

**영향 파일**: `transfer-service/.../Transfer.java`, `TransferService.java`, `TransferController.java`

```java
// Transfer.java 엔티티 인덱스 추가
@Table(name = "transfers", indexes = {
    @Index(name = "idx_transfer_sender", columnList = "senderUserId"),
    @Index(name = "idx_transfer_receiver", columnList = "receiverUserId")
})

// TransferController.java
@GetMapping
public Page<TransferResponse> getTransfers(
    @RequestHeader("X-User-Id") Long userId,
    @PageableDefault(size = 20, sort = "createdAt", direction = DESC) Pageable pageable
) {
    return transferService.getTransfers(userId, pageable);
}
```

---

### 3-D. getAgencyCreditSummary COUNT/SUM 쿼리 최적화 [M-4]

**영향 파일**: `reward-service/.../RewardService.java`, `RewardTaskRepository.java`

```java
// 전체 목록 로드 후 size() → COUNT 쿼리로 교체
long pendingApprovalCount = rewardTaskRepository.countByAgencyUserIdAndStatus(requestUserId, SUBMITTED);

// 전체 목록 메모리 필터링 → DB SUM으로 교체
BigDecimal monthlyPaid = rewardTaskRepository.sumRewardAmountByAgencyAndStatusAndMonth(
    requestUserId, PAID, yearMonth
);
```

---

### 3-E. JwtAuthenticationFilter ObjectMapper 주입 [M-5]

**영향 파일**: `api-gateway/.../JwtAuthenticationFilter.java`

수동 JSON 문자열 생성을 `ObjectMapper.writeValueAsString()`으로 교체.

---

### 3-F. PROCESSING 상태 고착 모니터링 [M-6]

**영향 파일**: `transfer-service` 신규 스케줄러 또는 `OutboxEventRelay` 확장

30분 이상 PROCESSING 상태인 Transfer를 감지하고 로그 경고 또는 Slack/알림 발송.

```java
@Scheduled(fixedDelay = 300_000)  // 5분마다
public void alertStuckTransfers() {
    LocalDateTime threshold = LocalDateTime.now().minusMinutes(30);
    List<Transfer> stuck = transferRepository.findByStatusAndCreatedAtBefore(PROCESSING, threshold);
    if (!stuck.isEmpty()) {
        log.warn("STUCK PROCESSING transfers detected: count={}", stuck.size());
        // 알림 발송 로직
    }
}
```

---

### 3-G. OpenBanking 토큰 키 유도: PBKDF2 도입 [M-7]

**영향 파일**: `banking-service/.../TokenCryptoService.java`

```java
private byte[] deriveKey(String secret, byte[] salt) throws Exception {
    KeySpec spec = new PBEKeySpec(secret.toCharArray(), salt, 310_000, 256);
    SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
    return factory.generateSecret(spec).getEncoded();
}
```

키 유도 함수 변경은 기존 암호화된 토큰과의 호환성 문제가 생기므로, 마이그레이션 계획과 함께 진행합니다.

---

### 3-H. OutboxEventRelay 분산 스케줄러 락 [M-8]

**영향 파일**: `transfer-service/.../OutboxEventRelay.java`

ShedLock 라이브러리를 도입하여 여러 인스턴스에서 스케줄러가 중복 실행되지 않도록 합니다.

```java
@Scheduled(fixedDelay = 1000)
@SchedulerLock(name = "outboxEventRelay", lockAtMostFor = "PT30S", lockAtLeastFor = "PT1S")
public void relay() {
    ...
}
```

---

## 수정 순서 요약

```
Phase 1 (즉시)
  └─ 1-A GatewayRequestFilter Fail-Closed
  └─ 1-B 상수 시간 시크릿 비교
  └─ 1-C LedgerController userId 필터
  └─ 1-D TossPaymentController 역할 검증
  └─ 1-E payMission 비관적 락
  └─ 1-F .env 패스워드 교체

Phase 2 (다음 스프린트)
  └─ 2-A ddl-auto → Flyway 전환
  └─ 2-B UserService Outbox 또는 재시도
  └─ 2-C OpenBanking 키 배포 안전 장치
  └─ 2-D Kafka DLT 설정
  └─ 2-E TossPaymentService cancel 락

Phase 3 (코드 품질)
  └─ 3-A 전체 서비스 예외 로깅
  └─ 3-B 로그인 Rate Limiting
  └─ 3-C 거래 내역 페이지네이션 + 인덱스
  └─ 3-D 보상 요약 쿼리 최적화
  └─ 3-E ObjectMapper 직렬화 교체
  └─ 3-F PROCESSING 고착 모니터링
  └─ 3-G PBKDF2 키 유도 (마이그레이션 필요)
  └─ 3-H ShedLock 분산 스케줄러
```

---

## 참고

- 상세 이슈 설명: [`docs/security-review.md`](../security-review.md)
- 진행 현황: [`docs/CHECKLIST.md`](../CHECKLIST.md) — "보안 및 코드 품질 개선" 섹션

