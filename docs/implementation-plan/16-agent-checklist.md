# 16. Agent Checklist

이 문서는 AI agent가 구현할 때 매번 확인해야 하는 체크리스트다.

## 작업 시작 전

- [ ] 현재 브랜치와 작업 디렉터리를 확인한다.
- [ ] 사용자가 만든 변경사항을 되돌리지 않는다.
- [ ] 구현 대상 서비스의 `build.gradle`과 `application.yml`을 먼저 읽는다.
- [ ] 관련 문서를 `docs/implementation-plan`에서 먼저 읽는다.
- [ ] 서비스 간 DB 직접 접근을 만들지 않는다.

## 코드 작성 중

- [ ] JPA Entity를 API Response로 직접 반환하지 않는다.
- [ ] 금액은 `BigDecimal`을 사용한다.
- [ ] 상태는 enum으로 관리한다.
- [ ] 외부 요청 DTO에는 validation annotation을 붙인다.
- [ ] DB 변경 메서드는 `@Transactional`을 사용한다.
- [ ] 실패 상태와 실패 원인을 저장한다.
- [ ] 중복 이벤트 처리를 고려한다.
- [ ] 로그에 민감정보를 남기지 않는다.

## 송금 구현 시

- [ ] `Idempotency-Key`가 없으면 실패시킨다.
- [ ] 동일 key + 동일 body는 같은 응답을 반환한다.
- [ ] 동일 key + 다른 body는 409로 실패시킨다.
- [ ] 잔액 차감은 wallet-service를 통해서만 한다.
- [ ] 송금 성공 시 OutboxEvent를 저장한다.
- [ ] Kafka는 DB 트랜잭션 안에서 직접 발행하지 않는다.
- [ ] 상태 전이는 허용된 경로만 통과하게 한다.
- [ ] 실패 상태에는 failureReason을 남긴다.
- [ ] sender 차감 성공 후 receiver 증가 실패는 COMPENSATION_REQUIRED로 남긴다.

## 결제 핵심 5요소 구현 시

- [ ] 상태 머신은 enum과 전이 검증 로직을 함께 구현한다.
- [ ] Idempotency는 key만 저장하지 말고 requestHash와 기존 response까지 저장한다.
- [ ] Outbox는 비즈니스 DB 트랜잭션 안에 저장하고 Kafka 발행은 별도 publisher가 처리한다.
- [ ] Retry는 네트워크성 오류에 제한하고, 비즈니스 오류에는 적용하지 않는다.
- [ ] DLQ에는 원본 이벤트, consumer group, 실패 원인, 시도 횟수를 남긴다.
- [ ] Mock PG는 성공뿐 아니라 실패, timeout, 처리 중, 거래 ID 중복을 시뮬레이션한다.

## 지갑 구현 시

- [ ] 잔액 음수를 허용하지 않는다.
- [ ] 잔액 변경 이력을 반드시 저장한다.
- [ ] 동시성 테스트를 작성한다.
- [ ] 차감/증가 API는 referenceId를 받는다.
- [ ] 같은 referenceId 중복 처리 정책을 고려한다.

## 오픈뱅킹 구현 시

- [ ] MockOpenBankingClient를 먼저 구현한다.
- [ ] 성공, 명시 실패, timeout, 처리 중, bank_tran_id 중복을 mock으로 검증한다.
- [ ] 충전은 출금이체 성공 후 wallet-service deposit까지 닫힌 루프로 만든다.
- [ ] Idempotency-Key + requestHash로 API 재요청을 방어한다.
- [ ] bank_tran_id unique 제약으로 은행망 중복을 방어한다.
- [ ] wallet referenceType/referenceId로 지갑 중복 반영을 방어한다.
- [ ] timeout, 처리 중, 중복 bank_tran_id는 결과조회 대상으로 남긴다.
- [ ] UNKNOWN/BANK_PROCESSING 상태를 결과조회 워커로 확정한다.
- [ ] 출금은 초기에 보상 근거를 남기고, hold 모델은 후속 확장으로 둔다.
- [ ] 정보제공자 API는 초기 구현 범위에 넣지 않는다.

## Reward Service 구현 시

- [ ] reward-service가 user/wallet/transfer DB를 직접 읽거나 쓰지 않는다.
- [ ] 가족 연결 API는 Family 관계의 parentUserId/childUserId와 연결 상태를 검증한다.
- [ ] 초대 코드는 만료 시간을 가진다.
- [ ] 가족 연결 해제 시 진행 중 미션 처리 정책을 적용한다.
- [ ] 보상 금액은 `BigDecimal`로 처리한다.
- [ ] 미션 상태는 enum으로 관리한다.
- [ ] 부모/아이 권한 검증을 API별로 수행한다.
- [ ] 미션 수정/취소는 허용된 상태에서만 가능하다.
- [ ] 제출/재제출 시 제출 메모와 evidenceImageUrl을 저장한다.
- [ ] 승인 API는 이미 `PAID`인 미션에 대해 중복 지급하지 않는다.
- [ ] transfer-service 호출 시 `reward-payment-{missionSubmissionId}` 형식의 고정 Idempotency-Key를 사용한다.
- [ ] transfer-service 실패 또는 timeout 후 재시도해도 같은 mission이 두 번 지급되지 않는다.
- [ ] 캐시북 합계는 `PAID` 상태 미션만 기준으로 계산한다.
- [ ] 보상 지급 완료 시 캐시북 수입 기록을 한 번만 생성한다.
- [ ] 알림 목록/읽음 처리 API를 구현한다.
- [ ] 인증 사진 업로드 URL API는 담당 아이만 호출할 수 있다.
- [ ] 지급 완료 후 transferId와 paidAt을 저장한다.
- [ ] 데모 시나리오에서 부모 지갑 잔액 감소와 아이 지갑 잔액 증가를 확인한다.

## User/Settings 구현 시, 보강/2차

- [ ] 회원가입 요청에 `role`을 받고 JWT claim에도 role을 넣는다.
- [ ] 프로필 조회/수정 API를 구현한다.
- [ ] 알림 설정 조회/수정 API를 구현한다.
- [ ] 로그아웃은 JWT 블랙리스트 사용 여부를 정책으로 정하고 구현 또는 문서화한다.

## 원장 구현 시

- [ ] sourceEventId 중복 처리를 한다.
- [ ] 원장 라인은 2개 생성한다.
- [ ] 원장 데이터는 수정하지 않는다.
- [ ] 정정은 보정 거래로 처리한다.

## 정산 구현 시

- [ ] 같은 날짜 중복 정산을 방지한다.
- [ ] 수수료 계산을 단위 테스트한다.
- [ ] settlement-service는 profile 실행을 유지한다.

## 작업 완료 전

- [ ] 해당 서비스 `bootJar`를 실행한다.
- [ ] 관련 테스트를 실행한다.
- [ ] `docker compose config --quiet`를 실행한다.
- [ ] README 또는 구현 문서와 달라진 점이 있으면 문서를 갱신한다.
- [ ] 최종 응답에 변경 파일과 검증 결과를 요약한다.
