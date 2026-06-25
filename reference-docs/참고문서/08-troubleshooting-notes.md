# Troubleshooting Notes

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

포트폴리오와 블로그에 사용할 수 있는 트러블슈팅 정리입니다.

## 1. 송금 완료 후 원장 기록이 누락될 수 있는 문제

### Problem

송금을 `SUCCEEDED`로 저장한 뒤 Kafka 이벤트 발행이 실패하면 ledger-service가 송금 완료 사실을 알 수 없습니다.

### Decision

transfer-service에 `outbox_events` 테이블을 두고 송금 상태 변경과 이벤트 저장을 같은 DB 트랜잭션으로 묶었습니다.

### Result

Kafka 발행이 실패해도 outbox row가 남기 때문에 relay가 재시도할 수 있습니다.

### Lesson

이벤트 발행은 비즈니스 상태 변경과 같은 원자성으로 다루거나, 최소한 복구 가능한 발행 의도를 저장해야 합니다.

## 2. 같은 송금 요청이 반복될 때 중복 차감되는 문제

### Problem

클라이언트 재시도, 네트워크 timeout, 사용자의 중복 클릭으로 같은 송금 요청이 여러 번 들어올 수 있습니다.

### Decision

transfer-service는 `Idempotency-Key`와 `requestHash`를 저장합니다. wallet-service는 `referenceType` + `referenceId`로 잔액 반영 중복을 막습니다.

### Result

API 요청 중복과 지갑 반영 중복을 각각 다른 계층에서 방어합니다.

### Lesson

결제성 API에서는 controller 단에서 중복 클릭을 막는 것만으로 충분하지 않습니다. 서버와 DB 제약까지 멱등하게 설계해야 합니다.

## 3. 출금 성공 후 입금 실패 문제

### Problem

송금은 sender 출금과 receiver 입금이라는 두 단계로 나뉩니다. 출금은 성공했는데 입금이 실패하면 단순 실패로 처리할 수 없습니다.

### Decision

`COMPENSATION_REQUIRED` 상태를 만들고, sender wallet에 환불하는 compensation refund API를 별도로 두었습니다.

### Result

부분 실패가 발생해도 상태와 실패 사유를 남기고 나중에 복구할 수 있습니다.

### Lesson

분산 트랜잭션을 억지로 구현하기보다, 실패를 상태로 드러내고 보상 경로를 명확히 두는 것이 현실적입니다.

## 4. Open Banking 응답을 신뢰하기 어려운 문제

### Problem

외부 금융 API는 timeout, 처리 중, 권한 없음 등으로 결과가 모호할 수 있습니다. HTTP 200이 항상 최종 금융 성공을 뜻하지도 않습니다.

### Decision

은행 거래 상태를 세분화하고 result-check API와 scheduler로 최종 상태를 확인합니다. 권한 없는 API 응답은 business success로 연결하지 않습니다.

### Result

모호한 응답을 실패로 단정하거나 성공으로 오판하지 않고 추적 가능한 상태로 남깁니다.

### Lesson

외부 금융 연동에서는 기술적 성공과 도메인 성공을 분리해야 합니다.

## 5. Docker Compose 전체 실행 검증 지연

### Problem

로컬 환경에서 Docker Desktop이 실행 중이 아니면 전체 compose smoke test를 완료할 수 없습니다.

### Decision

우선 `docker compose config --quiet`, 서비스별 테스트, 프론트엔드 타입/e2e 검증으로 정적/부분 검증을 수행하고, 전체 runtime smoke는 별도 체크리스트에 남깁니다.

### Result

검증한 것과 검증하지 못한 것을 문서에 명확히 구분했습니다.

### Lesson

포트폴리오 문서에서는 "다 됐다"보다 검증 범위와 미검증 리스크를 솔직하게 구분하는 것이 신뢰도를 높입니다.

