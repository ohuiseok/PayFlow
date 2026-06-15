# 14. Testing Strategy

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

reward 보상 지급 흐름

ledger 전표 저장 흐름

## Contract Tests

서비스 간 요청/응답 DTO

공통 에러 응답 형식

gateway 헤더 전달

## E2E Scenarios

1. 부모 회원 가입
2. 자녀 회원 가입
3. 부모 지갑 충전
4. 부모-자녀 연결
5. 미션 생성
6. 자녀 미션 제출
7. 부모 미션 승인
8. 보상 지급
9. 자녀 지갑 잔액 확인
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
