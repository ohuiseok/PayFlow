# 00. Implementation Overview

이 문서는 PayFlow를 구현할 AI agent가 전체 방향을 잃지 않도록 하는 최상위 안내서다.

PayFlow는 "MSA 기반 간편결제 포트폴리오"이며, 핵심은 단순 CRUD가 아니라 결제 시스템에서 중요한 정합성, 멱등성, 원장, 이벤트 발행 신뢰성, 정산 흐름을 구현하는 것이다.

## 최종 목표

아래 서비스를 구현한다.

```text
api-gateway
user-service
wallet-service
transfer-service
ledger-service
settlement-service

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
3. 원장의 진실은 `ledger-service`만 가진다.
4. 서비스는 다른 서비스의 DB를 직접 읽거나 쓰지 않는다.
5. 모든 송금 요청은 `Idempotency-Key`를 필수로 요구한다.
6. Kafka 이벤트는 DB 트랜잭션 안에서 직접 발행하지 않는다.
7. Kafka 이벤트 발행은 Transactional Outbox Pattern으로 처리한다.
8. 원장 데이터는 수정하지 않고, 보정 거래로만 정정한다.
9. 실패 상태는 숨기지 말고 상태와 원인을 저장한다.
10. 테스트는 정상 케이스보다 중복 요청, 동시 요청, 장애, 재시도를 더 중요하게 다룬다.

## 구현 순서

반드시 아래 순서로 구현한다.

```text
01. 공통 개발 규칙과 패키지 규칙 정리
02. DB 스키마와 마이그레이션 전략 결정
03. user-service 최소 인증 구현
04. wallet-service 지갑/잔액/이력 구현
05. transfer-service 송금 상태와 멱등성 구현
06. Transfer -> Wallet 동기 호출 구현
07. Redis 분산 락 적용
08. Transactional Outbox 구현
09. Kafka 이벤트 발행 구현
10. ledger-service 원장 기록 구현
11. settlement-service 정산 배치 구현
12. API Gateway 인증/라우팅 보강
13. 장애/재시도/복구 시나리오 구현
14. 테스트 시나리오 작성
15. 배포와 운영 문서 정리
```

## 구현 범위

처음부터 너무 넓히지 않는다.

1차 완성 범위:

```text
회원가입
로그인
JWT 발급
지갑 생성
잔액 충전
잔액 조회
송금 요청
송금 상태 조회
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
프론트엔드
실제 외부 은행 API 연동
복잡한 권한 시스템
OAuth
Refresh Token rotation
Kubernetes
Prometheus/Grafana 상시 실행
```

## 포트폴리오에서 강조할 문장

구현 중 설계 판단이 필요하면 아래 방향을 우선한다.

```text
제한된 단일 EC2 환경에서도 MSA 구조를 유지하되,
결제 시스템의 핵심인 잔액 정합성, 멱등성, 원장, 이벤트 발행 신뢰성을 우선 구현했다.
```

