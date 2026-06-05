# 00. Implementation Overview

이 문서는 PayFlow를 구현할 AI agent가 전체 방향을 잃지 않도록 하는 최상위 안내서다.

PayFlow는 "MSA 기반 부모-자녀 미션 보상 지갑 서비스"이며, 핵심은 단순 CRUD가 아니라 결제 시스템에서 중요한 정합성, 멱등성, 원장, 이벤트 발행 신뢰성, 정산 흐름을 구현하는 것이다.

구현 대상 API의 기준은 `docs/api-spec.md`이고, 서비스와 화면 흐름의 기준은 `docs/service-flow.md`이다.

## 최종 목표

아래 서비스를 구현한다.

```text
api-gateway
user-service
wallet-service
banking-service
transfer-service
reward-service
ledger-service
settlement-service
notification-service 또는 reward-service 내부 notification 모듈
file-service 또는 reward-service 내부 file 모듈

infra:
mysql
redis
kafka
nginx
```

`settlement-service`는 Docker Compose profile로 필요할 때만 실행한다.

## 가장 중요한 설계 원칙

1. 잔액의 진실은 `wallet-service`만 가진다.
2. 송금 상태의 진실은 `transfer-service`만 가진다.
3. 가족 연결, 보상 미션, 캐시북 기록의 진실은 `reward-service`만 가진다.
4. 원장의 진실은 `ledger-service`만 가진다.
5. 외부 은행망 연동 상태의 진실은 `banking-service`만 가진다.
6. 서비스는 다른 서비스의 DB를 직접 읽거나 쓰지 않는다.
7. 모든 송금 요청은 `Idempotency-Key`를 필수로 요구한다.
8. 외부 은행 API 성공이 확정되기 전에는 지갑 잔액을 변경하지 않는다.
9. Kafka 이벤트는 DB 트랜잭션 안에서 직접 발행하지 않는다.
10. Kafka 이벤트 발행은 Transactional Outbox Pattern으로 처리한다.
11. 원장 데이터는 수정하지 않고, 보정 거래로만 정정한다.
12. 실패 상태는 숨기지 말고 상태와 원인을 저장한다.
13. 테스트는 정상 케이스보다 중복 요청, 동시 요청, 장애, 재시도를 더 중요하게 다룬다.

## 구현 순서

반드시 아래 순서로 구현한다.

```text
01. 공통 개발 규칙과 패키지 규칙 정리
02. DB 스키마와 마이그레이션 전략 결정
03. user-service 인증, 역할, 프로필, 설정 기반 구현
04. wallet-service 지갑/잔액/이력 구현
05. transfer-service 송금 상태와 멱등성 구현
06. reward-service 가족 연결, 미션, 캐시북, 보상 지급 연동 구현
07. banking-service 크레딧 충전/계좌/오픈뱅킹 상태 모델 구현
08. Redis 분산 락 적용
09. Transactional Outbox와 Kafka 이벤트 발행 구현
10. ledger-service 원장 기록 구현
11. settlement-service 정산 배치 구현
12. API Gateway 인증/라우팅 보강
13. 장애/재시도/복구 시나리오 구현
14. 테스트 시나리오 작성
15. 로컬 실행과 배포 문서 정리
16. Agent 체크리스트 기준으로 구현 완료 전 점검
17. 결제 핵심 5요소 고도화 로드맵 반영
```

## 현재 구현 기반과 다음 고도화 방향

현재 구현된 `user-service`, `wallet-service`, `api-gateway` 기반은 유지한다.
다음 단계에서는 결제 시스템의 신뢰성을 보여주는 핵심 5요소를 `transfer-service`, `banking-service`, Kafka/Outbox, 장애 복구 흐름에 녹여 구현한다.

```text
핵심 5요소:
1. 상태 머신
2. Idempotency
3. Outbox Pattern
4. Retry/DLQ
5. Mock PG
```

이 5요소는 별도 과제가 아니라 하나의 결제 흐름으로 연결한다.

```text
Mock PG/Wallet 응답
-> 상태 머신 전이
-> Idempotency로 중복 요청 방어
-> Outbox로 후속 이벤트 저장
-> Retry/DLQ로 실패와 재처리 근거 보존
```

상세 로드맵:

```text
docs/implementation-plan/17-payment-core-hardening-roadmap.md
```

## 구현 범위

처음부터 너무 넓히지 않는다.

1차 완성 범위:

```text
회원가입
역할 선택
로그인
JWT 발급
프로필/설정 조회
가족 연결
지갑 생성
잔액 충전
잔액 조회
오픈뱅킹 테스트베드 기반 충전 상태 기록
부모 크레딧 충전
송금 요청
송금 상태 조회
용돈 미션 등록
아이 완료 요청
부모 승인 후 실제 용돈 지급
반려와 재제출
아이 캐시북 조회
부모 지급/정산 내역 조회
알림 목록/읽음 처리
인증 사진 업로드 URL 발급
멱등성 처리
분산 락
Outbox 저장
Kafka 발행
원장 기록
```

2차 확장 범위:

```text
정산 배치
수수료 계산
오픈뱅킹 출금/환불
반복 미션
가족 관계 고도화
Push 알림 외부 연동
파일 저장소 S3 연동
장애 재처리
Outbox publisher 스케줄링 고도화
대사/reconciliation
k6 부하 테스트
GitHub Actions 배포
```

## 구현하지 않을 것

초기에는 아래를 구현하지 않는다.

```text
관리자 페이지
운영용 프론트엔드
복잡한 권한 시스템
Refresh Token rotation
Kubernetes
Prometheus/Grafana 상시 실행
```

`sample-react` 목업/더미데이터 앱은 화면 흐름 검증용 산출물로 유지하되, 운영용 모바일 앱/관리자 웹은 초기 구현 범위에서 제외한다.
초기 포트폴리오에서는 금융결제원 오픈뱅킹 테스트베드 또는 mock profile을 대상으로 구현한다.
운영 은행망 실거래, 이용기관 심사, 실제 정산 계좌 운용은 구현 범위에서 제외한다.

## 포트폴리오에서 강조할 문장

구현 중 설계 판단이 필요하면 아래 방향을 우선한다.

```text
제한된 단일 EC2 환경에서도 MSA 구조를 유지하되,
외부 은행망 연동과 내부 지갑 잔액의 경계를 분리하고,
결제 시스템의 핵심인 잔액 정합성, 멱등성, 원장, 이벤트 발행 신뢰성을 우선 구현했다.
```
