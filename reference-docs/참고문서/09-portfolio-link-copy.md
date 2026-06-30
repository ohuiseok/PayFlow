# Portfolio Link Copy

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

포트폴리오 화면의 링크 버튼 주변에 붙일 짧은 설명입니다.

## Link Labels

```text
GitHub | API Docs | ERD | Architecture | Load Test Report
```

## Short Descriptions

### GitHub

전체 소스코드, 로컬 실행 방법, 테스트 명령, 서비스별 구현을 확인할 수 있습니다.

### API Docs

Gateway 라우팅, 인증, 멱등성 헤더, 지갑/송금/미션/오픈뱅킹/정산 API 계약을 정리했습니다.

### ERD

서비스별 독립 DB, 지갑 거래 이력, 송금, outbox, 원장, 정산 테이블과 주요 제약 조건을 정리했습니다.

### Architecture

API Gateway, MySQL, Redis, Kafka, transactional outbox, 보상 트랜잭션 흐름을 설명합니다.

### Load Test Report

송금/보상 지급 부하 테스트 시나리오, 관찰 지표, 병목 분석 기준을 정리했습니다.

## Portfolio Project Summary

PayFlow는 청년 정책 미션 지원금 도메인을 기반으로 지갑 잔액 정합성, 멱등성, 송금 실패 복구, Kafka 기반 원장 기록과 Toss 일별 정산 대사를 구현한 MSA 포트폴리오 프로젝트입니다.

## 3-Line Version

PayFlow는 기관 담당자가 청년 참여자에게 미션을 부여하고 지원금을 지급하는 지갑 서비스입니다.
Spring Boot MSA, MySQL, Redis, Kafka, API Gateway로 구성했습니다.
멱등성, 지갑 잔액 정합성, transactional outbox, 보상 트랜잭션을 핵심 설계 포인트로 구현했습니다.

## Resume Bullet Version

- Spring Boot MSA 기반 청년 정책 참여 미션 및 지원금 지급 플랫폼 구현
- API Gateway 인증 라우팅, 서비스별 DB 분리, wallet-service 중심 잔액 정합성 설계
- `Idempotency-Key`, request hash, wallet reference 기반 중복 지급 방지 구현
- Redis lock과 transactional outbox, Kafka 이벤트를 활용한 송금/원장 기록 흐름 구현
- Spring Batch 기반 Toss 승인/취소 일별 집계, 수수료 계산, 원장 대사 구현
- Open Banking 연동에서 모호한 응답, 권한 제한 API, 보상 처리 상태를 명시적으로 모델링

