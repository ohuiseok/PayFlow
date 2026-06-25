# 14. Testing Strategy

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

테스트는 포트폴리오에서 설계 역량을 보여주는 핵심 산출물이다.

## Unit Tests

도메인 상태 전이

금액 검증

권한 검증

멱등 요청 비교

차대 일치 검증

## Integration Tests

Repository unique 제약

wallet 잔액 변경 transaction

transfer 성공/실패 흐름

banking 충전 흐름

reward 지원금 지급 흐름

ledger 전표 저장 흐름

## Contract Tests

서비스 간 요청/응답 DTO

공통 에러 응답 형식

gateway 헤더 전달

## E2E Scenarios

1. 기관 담당자 회원 가입
2. 청년 참여자 회원 가입
3. 기관 지갑 충전
4. 기관-청년 참여자 연결
5. 미션 생성
6. 청년 정책 미션 제출
7. 기관 정책 미션 승인
8. 지원금 지급
9. 청년 지갑 잔액 확인
10. 원장 기록 확인

## Required Failure Scenarios

잔액 부족 송금 실패

동일 멱등키 재시도

동일 멱등키 요청 본문 충돌

동시 출금 잔액 보호

차대 불일치 원장 저장 실패

## Tools

JUnit 5

AssertJ

Mockito

Spring Boot Test

Testcontainers MySQL


