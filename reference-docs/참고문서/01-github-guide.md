# GitHub 안내 문서

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

이 문서는 GitHub 저장소 README 또는 포트폴리오의 GitHub 링크 설명에 사용할 수 있는 정리본입니다.

## 저장소 요약

PayFlow는 청년 정책 참여 미션 및 지원금 지급 플랫폼입니다. 단순 CRUD가 아니라 지갑 잔액, 송금, 멱등성, 보상 처리, 이벤트 기반 원장 기록처럼 결제 시스템에서 중요한 주제를 포트폴리오 범위로 구현했습니다.

핵심 사용자 흐름:

```text
회원가입
-> 사용자별 지갑 생성
-> 기관 지갑 충전
-> 기관-청년 참여자 연결
-> 미션 생성
-> 청년 제출
-> 기관 승인
-> 기관 지갑에서 청년 지갑으로 보상 송금
-> 지갑 거래 이력과 원장 기록 생성
-> Toss 승인/취소 이벤트 일별 정산과 원장 대사
```

## 기술 스택

| 영역 | 기술 |
| --- | --- |
| 백엔드 | Java, Spring Boot, Spring Web, Spring Data JPA, Spring Batch |
| 게이트웨이 | Spring Cloud Gateway |
| 데이터베이스 | MySQL |
| 캐시/락 | Redis |
| 메시징 | Kafka |
| 프론트엔드 | React Native/Expo 샘플 앱 |
| 인프라 | Docker Compose, Nginx |
| 테스트 | JUnit, Spring Boot Test, Playwright, API 스모크 스크립트 |

## 서비스 구성

| 서비스 | 책임 |
| --- | --- |
| api-gateway | 외부 API 진입점, JWT 검증, 사용자 헤더 주입 |
| user-service | 회원가입, 로그인, 사용자 조회 |
| wallet-service | 지갑 생성, 잔액 조회, 입금/출금, 거래 이력 |
| banking-service | 계좌 등록, 충전/출금 요청, Open Banking 연동 모델 |
| transfer-service | 사용자 간 송금, 멱등성, Redis 락, outbox 이벤트 |
| reward-service | 기관-청년 참여자 연결, 미션 생성/제출/승인/보상 지급 |
| ledger-service | 송금 이벤트 소비, 원장/실패 기록 |
| settlement-service | Toss 승인/취소 이벤트 수집, 일별 집계, 수수료 계산, 원장 대사 |

## 로컬 실행

```powershell
docker compose up --build
```

기본 진입점:

```text
프론트엔드: http://localhost:19006
API Gateway: http://localhost:8080
Nginx: http://localhost
```

환경 변수 예시는 `.env.example`을 기준으로 확인합니다.

## 검증 명령어

백엔드 서비스 테스트:

```powershell
.\gradlew.bat test
```

프론트엔드 검증:

```powershell
cd sample-react
npm run check
```

Docker Compose 설정 검증:

```powershell
docker compose config --quiet
```

API 스모크 테스트:

```powershell
.\scripts\api-smoke.ps1
```

## 리뷰어 추천 읽기 순서

처음 보는 리뷰어라면 아래 순서로 보면 프로젝트 의도가 가장 빨리 잡힙니다.

1. `docs/payflow-service-planning.md`
2. `docs/service-flow.md`
3. `docs/api-spec.md`
4. `docs/erd.md`
5. `docs/portfolio-open-banking.md`
6. `reference-docs/참고문서/04-architecture.md`

## 포트폴리오 강조 항목

- 지갑 잔액 변경은 wallet-service에서만 수행합니다.
- 송금 요청은 `Idempotency-Key`와 요청 본문 해시로 중복 처리를 방어합니다.
- wallet transaction은 `referenceType` + `referenceId`로 중복 반영을 막습니다.
- 송금 중 동일 지갑에 대한 경쟁 상태를 줄이기 위해 Redis 락을 사용합니다.
- 송금 완료/실패 이벤트는 transfer-service DB에 outbox로 저장한 뒤 Kafka로 발행합니다.
- ledger-service는 Kafka 이벤트를 소비하고 같은 transferId를 중복 기록하지 않습니다.
- banking-service는 Toss 승인/취소와 정산 outbox를 같은 트랜잭션에 저장합니다.
- settlement-service는 `payment.settlement`를 멱등 소비하고 Spring Batch로 기준일별 원장 대사를 수행합니다.
- Open Banking 응답은 HTTP 성공과 금융 성공을 분리해 상태로 모델링합니다.
