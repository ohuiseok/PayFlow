# PayFlow 보안 및 코드 품질 리뷰

**리뷰 날짜**: 2026-06-20  
**리뷰 범위**: 8개 마이크로서비스 전체 Java 소스 (237개 파일)  
**관련 계획 문서**: [`implementation-plan/17-security-and-improvements.md`](implementation-plan/17-security-and-improvements.md)

---

## 심각도 분류

| 등급 | 기준 |
|------|------|
| 🔴 Critical | 즉시 수정. 자금 손실, 인증 우회, 개인정보 유출 가능 |
| 🟠 High | 중요. 데이터 정합성 오류, 운영 장애 가능 |
| 🟡 Medium | 개선 권장. 성능 저하, 운영 가시성 부족 |
| 🟢 Good | 잘 구현된 부분 |

---

## 🔴 Critical

### [C-1] GatewayRequestFilter: 시크릿 미설정 시 인증 전체 우회

**영향 서비스**: 전체 7개 서비스 (user, wallet, banking, transfer, reward, ledger, settlement)

```java
// 현재 코드 (모든 서비스 동일)
private boolean hasTrustedSecret(HttpServletRequest request) {
    if (!StringUtils.hasText(gatewayInternalSecret) && !StringUtils.hasText(internalSecret)) {
        return true;  // ← 환경변수 미설정 시 모든 요청 허용
    }
    ...
}
```

환경변수(`INTERNAL_SERVICE_SECRET`, `GATEWAY_INTERNAL_SECRET`)가 비어 있으면 외부 요청을 내부 요청으로 간주하여 허용합니다. 잘못된 배포 한 번으로 서비스 전체가 인증 없이 열립니다.

```java
// 수정 방향: Fail-Closed (기본 거부)
private boolean hasTrustedSecret(HttpServletRequest request) {
    if (!StringUtils.hasText(gatewayInternalSecret) && !StringUtils.hasText(internalSecret)) {
        return false;  // 시크릿 미설정 시 거부
    }
    ...
}
```

---

### [C-2] 내부 시크릿 비교: 타이밍 공격(Timing Attack) 취약점

**영향 파일**: 모든 서비스 `GatewayRequestFilter.java`, `WalletController.java`

```java
// 현재 코드
return gatewayInternalSecret.equals(gatewaySecret);  // String.equals는 첫 불일치 즉시 반환
```

응답 시간 차이로 시크릿의 일치 여부를 추측할 수 있습니다.

```java
// 수정 방향: 상수 시간 비교
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

MessageDigest.isEqual(
    internalSecret.getBytes(StandardCharsets.UTF_8),
    requestSecret.getBytes(StandardCharsets.UTF_8)
);
```

---

### [C-3] .env: 취약한 패스워드 및 기본값 경고 미처리

**영향 파일**: `.env`

```bash
MYSQL_ROOT_PASSWORD=root       # 브루트포스 1순위
MYSQL_PASSWORD=payflow         # 서비스명과 동일
JWT_SECRET=payflow-local-rotated-jwt-secret-20260619-replace-before-prod
```

- MySQL 패스워드가 지나치게 단순합니다.
- JWT 시크릿에 `replace-before-prod` 문구가 있으나 코드 리뷰 없이 배포될 위험이 있습니다.
- 운영 환경에서는 HashiCorp Vault, AWS Secrets Manager, Kubernetes Secrets 사용을 권장합니다.

---

### [C-4] LedgerController: 모든 사용자의 거래 원장 무단 노출

**영향 파일**: `ledger-service/.../LedgerController.java`

```java
// 현재 코드
@GetMapping("/entries")
public List<LedgerEntryResponse> getLedgerEntries() {
    return ledgerService.getLedgerEntries();  // userId 필터 없음
}

@GetMapping("/transfer-failures")
public List<TransferFailureEventResponse> getTransferFailures() {
    return ledgerService.getTransferFailures();  // userId 필터 없음
}
```

인증된 사용자 누구나 최근 100건의 전체 사용자 거래 내역(`senderUserId`, `receiverUserId`, `amount` 포함)을 조회할 수 있습니다. 개인정보보호법 위반 소지가 있습니다.

```java
// 수정 방향
@GetMapping("/entries")
public List<LedgerEntryResponse> getLedgerEntries(
    @RequestHeader("X-User-Id") Long requestUserId
) {
    return ledgerService.getLedgerEntries(requestUserId);
}
```

---

### [C-5] TossPaymentController: 운영 관리 API 역할 검증 없음

**영향 파일**: `banking-service/.../TossPaymentController.java`

```java
// 현재 코드
@GetMapping("/operations/summary")
public TossOperationalSummaryResponse getOperationalSummary() {
    return tossPaymentService.getOperationalSummary();  // 역할 체크 없음
}

@GetMapping("/operations/compensations")
public List<TossChargeSummaryResponse> getCompensationRequiredCharges() {
    return tossPaymentService.getCompensationRequiredCharges();  // 역할 체크 없음
}
```

일반 사용자(CHILD 포함)가 전체 결제 현황 및 보상 대기 내역을 조회할 수 있습니다.

수정 방향: `X-User-Role` 헤더로 ADMIN/PARENT 역할 확인, 또는 내부 전용 관리 서비스로 분리.

---

### [C-6] RewardService.payMission: 락 없는 이중 송금(TOCTOU)

**영향 파일**: `reward-service/.../RewardService.java`

```java
// 현재 코드
@Transactional
public MissionResponse payMission(Long missionId, Long requestUserId, String role) {
    RewardTask task = findMission(missionId);  // SELECT FOR UPDATE 없음
    if (task.getStatus() == RewardTaskStatus.PAID) return MissionResponse.from(task);
    // ← 동시 요청 2개가 여기까지 모두 통과
    transferClient.createTransfer(...);  // 두 번 실제 송금 발생 가능
    task.markPaid(response.transferId());
}
```

동시 요청 두 개가 상태 체크를 동시에 통과하여 실제 송금이 두 번 발생할 수 있습니다.

```java
// 수정 방향: RewardTaskRepository에 비관적 락 쿼리 추가
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT t FROM RewardTask t WHERE t.id = :id")
Optional<RewardTask> findByIdForUpdate(@Param("id") Long id);
```

---

## 🟠 High

### [H-1] 5개 서비스에 `ddl-auto=update` 설정

**영향**: user, wallet, transfer, reward, settlement 서비스

`banking`과 `ledger`만 Flyway를 사용하고, 나머지 5개 서비스는 Hibernate가 스키마를 자동 변경합니다. 운영 환경에서 컬럼 삭제 누락, 타입 변경 오류, 인덱스 미관리 등의 위험이 있습니다.

수정 방향: 모든 서비스를 `ddl-auto=validate` + Flyway 마이그레이션으로 전환.

---

### [H-2] UserService.createUser: 분산 트랜잭션 미처리로 고아 지갑 발생 가능

**영향 파일**: `user-service/.../UserService.java`

```java
@Transactional
public UserResponse createUser(CreateUserRequest request) {
    User savedUser = userRepository.saveAndFlush(user);  // DB 확정
    walletClient.createWallet(savedUser.getId());         // HTTP 호출 — 타임아웃 시 롤백
    // 결과: 지갑은 생성되었으나 사용자 레코드는 롤백 → 고아 지갑
}
```

수정 방향: Outbox 패턴으로 사용자 생성 완료 이벤트를 비동기 발행하거나, 지갑 생성 실패에 대한 보상 재시도 로직 추가.

---

### [H-3] OpenBanking 토큰 암호화 키 하드코딩 폴백

**영향 파일**: `banking-service/.../TokenCryptoService.java`

```java
// 환경변수 미설정 시 기본값 사용
new SecretKeySpec(sha256("payflow-local-token-secret"), "AES");
```

이 기본값을 아는 사람은 DB에 저장된 모든 OpenBanking 액세스 토큰을 복호화할 수 있습니다.

```java
// 수정 방향: 시작 시 유효성 검사로 기본값 배포 차단
@PostConstruct
void validate() {
    if (!StringUtils.hasText(encryptionSecret) || encryptionSecret.equals("payflow-local-token-secret")) {
        throw new IllegalStateException("OpenBanking 토큰 암호화 키가 설정되지 않았습니다.");
    }
}
```

---

### [H-4] 사용자가 직접 PARENT 역할 선택 가능

**영향 파일**: `user-service/.../CreateUserRequest.java`

```java
public record CreateUserRequest(
    ...
    @NotNull UserRole role  // 클라이언트가 PARENT / CHILD 자유 선택
) {}
```

누구든 회원가입 시 PARENT 역할로 등록하여 타인의 지갑 조회, 미션 생성, 송금 실행 권한을 가질 수 있습니다.

수정 방향: 초대 코드 또는 관리자 승인 기반 역할 부여 흐름 도입.

---

### [H-5] Kafka DLT 미설정: 독성 메시지 시 원장 처리 전체 중단

**영향 파일**: `ledger-service/.../TransferEventConsumer.java`

```java
// JsonProcessingException이 throws로 선언되어 Kafka 리스너로 전파
public void handleTransferCompleted(String payload) throws JsonProcessingException {
    ledgerService.recordTransfer(objectMapper.readValue(payload, ...));
}
```

파싱 불가 메시지(malformed JSON, 스키마 변경)가 들어오면 파티션 1개 구성에서 후속 메시지 처리가 전부 중단됩니다.

```java
// 수정 방향: DLT + 제한 재시도
@Bean
public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> template) {
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);
    return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
}
```

---

### [H-6] TossPaymentService.cancel: 동시 취소 멱등성 미보장

**영향 파일**: `banking-service/.../TossPaymentService.java`

`findByPaymentKey` 후 상태 체크에 비관적 락이 없어 동시 취소 요청 시 중복 취소가 발생할 수 있습니다.

수정 방향: `SELECT FOR UPDATE` 쿼리로 취소 처리 단계 보호.

---

## 🟡 Medium

### [M-1] GlobalExceptionHandler: 예외 로깅 누락

**영향 서비스**: 전체 (user, wallet, transfer, reward, ledger, settlement)

```java
// 현재 코드
@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleException(Exception exception) {
    // exception을 로깅하지 않음 → 운영에서 원인 파악 불가
    return ResponseEntity.status(500).body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
}
```

```java
// 수정 방향
private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleException(Exception exception) {
    log.error("Unhandled exception", exception);
    return ResponseEntity.status(500).body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR));
}
```

---

### [M-2] 로그인 브루트포스 방어 없음

Rate Limiting, 계정 잠금, 패스워드 복잡도 요구사항(`@Size(min=8)`만 존재)이 없습니다.

수정 방향: nginx 또는 api-gateway 레벨의 rate limiting, Redis 기반 로그인 시도 횟수 제한.

---

### [M-3] 거래 내역 조회: 페이지네이션 없음 + 인덱스 없음

**영향 파일**: `transfer-service/.../TransferService.java`

```java
// 전체 이력을 메모리에 로드
return transferRepository
    .findBySenderUserIdOrReceiverUserIdOrderByCreatedAtDesc(requestUserId, requestUserId)
    .stream().map(TransferResponse::from).toList();
```

`senderUserId`, `receiverUserId` 컬럼에 인덱스 없음. 거래가 많을수록 OOM 및 풀 스캔 위험.

수정 방향:
- `Transfer` 엔티티에 `@Index(columnList = "senderUserId")`, `@Index(columnList = "receiverUserId")` 추가
- `Pageable` 파라미터 도입

---

### [M-4] getParentCreditSummary: 전체 목록 로드 후 메모리 필터링

**영향 파일**: `reward-service/.../RewardService.java`

```java
// 승인 대기 건수를 위해 전체 목록 로드 후 .size()
long pendingApprovalCount = rewardTaskRepository
    .findByParentUserIdAndStatusInOrderByCreatedAtDesc(requestUserId, List.of(SUBMITTED)).size();
```

수정 방향: `COUNT` 및 `SUM` DB 쿼리로 대체.

---

### [M-5] JwtAuthenticationFilter: 수동 JSON 직렬화

**영향 파일**: `api-gateway/.../JwtAuthenticationFilter.java`

```java
// escapeJson이 \\ 와 " 만 처리. 개행문자, Unicode 제어문자 미처리
String body = """{"code":"%s","message":"%s",...}""".formatted(...);
```

수정 방향: `ObjectMapper`를 빈으로 주입하여 직렬화 처리.

---

### [M-6] Transfer PROCESSING 상태 고착 모니터링 없음

`markTransferFailed`가 실패하면 Transfer가 `PROCESSING` 상태로 영구 잔류합니다. 이를 감지하고 알리는 배치나 알림이 없습니다.

수정 방향: PROCESSING 상태가 임계 시간 초과 시 알림을 보내는 스케줄러 또는 모니터링 추가.

---

### [M-7] OpenBanking 토큰 암호화 키 유도에 단순 SHA-256 해시 사용

**영향 파일**: `banking-service/.../TokenCryptoService.java`

패스워드/시크릿에서 AES 키를 유도할 때 SHA-256 단순 해시를 사용합니다. 시크릿이 짧거나 예측 가능한 경우 사전 공격에 취약합니다.

수정 방향: PBKDF2, Argon2 같은 키 유도 함수(KDF) 사용.

---

### [M-8] OutboxEventRelay: recoverStuckProcessingEvents 분산 중복 실행

**영향 파일**: `transfer-service/.../OutboxEventRelay.java`

스케일 아웃 시 여러 인스턴스가 동시에 `recoverStuckProcessingEvents`를 실행합니다. 결과는 동일하나 불필요한 DB 부하가 발생합니다.

수정 방향: ShedLock 또는 Redis 기반 스케줄러 분산 락 도입.

---

## 🟢 잘 된 점

### [G-1] Outbox 패턴: 교과서적 구현

`transfer-service`의 Outbox 패턴이 잘 설계되었습니다.
- `TransferEventPublisher`가 같은 DB 트랜잭션 안에서 `OutboxEvent` 저장
- `claimPublishableEvent`가 원자적 UPDATE로 다중 인스턴스 경합 처리
- 타임아웃된 PROCESSING 상태 복구 로직 포함
- `maxRetries` 제한으로 독성 이벤트 무한 재시도 방지

### [G-2] Redis 분산 락: Lua 스크립트 원자적 해제

```java
// 소유자 토큰 검증 + 삭제를 Lua 스크립트로 원자적 처리
if redis.call('get', KEYS[1]) == ARGV[1] then
    return redis.call('del', KEYS[1])
end
```

### [G-3] WalletService: 동시성 안전 잔액 처리

`SELECT FOR UPDATE` + `WalletTransaction` unique constraint 조합으로 동시 출금 시 잔액 정합성 보장. `resolveDuplicateReference`로 네트워크 재시도 멱등성도 처리.

### [G-4] JWT 필터: X-User-* 헤더 선제 제거

게이트웨이 진입 즉시 클라이언트 헤더를 제거하여 헤더 주입 공격을 차단합니다.

### [G-5] 로그인: 사용자 열거 공격 방지

없는 사용자와 패스워드 오류를 동일한 `INVALID_CREDENTIALS`로 반환합니다.

### [G-6] Transfer 멱등성: requestHash + 동시 삽입 경합 처리

`DataIntegrityViolationException` 후 재조회 패턴으로 동시 삽입 경합을 안전하게 처리합니다.

### [G-7] OpenBanking OAuth state 서명 검증

userId + timestamp를 바인딩한 서명으로 CSRF를 방지합니다.

### [G-8] AES-256-GCM: 매 암호화마다 SecureRandom IV 생성

올바른 AES-GCM 구현입니다.

---

## 참고

- 개발 우선순위 및 수정 계획: [`implementation-plan/17-security-and-improvements.md`](implementation-plan/17-security-and-improvements.md)
- 수정 진행 현황: [`CHECKLIST.md`](CHECKLIST.md) — "보안 및 코드 품질 개선" 섹션
