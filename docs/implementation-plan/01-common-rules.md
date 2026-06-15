# 01. Common Rules

모든 서비스는 같은 응답 형식, 에러 코드, 인증 규칙을 따른다.

## Tech Baseline

Java 17

Spring Boot 3.x

Spring Security

Spring Data JPA

Flyway

MySQL 8.x

Docker Compose

## API Response

성공 응답:

```json
{
  "data": {},
  "traceId": "trace-id"
}
```

에러 응답:

```json
{
  "code": "ERROR_CODE",
  "message": "error message",
  "traceId": "trace-id"
}
```

## Error Codes

`VALIDATION_ERROR`

`UNAUTHORIZED`

`FORBIDDEN`

`NOT_FOUND`

`CONFLICT`

`INSUFFICIENT_BALANCE`

`IDEMPOTENCY_CONFLICT`

`EXTERNAL_SERVICE_ERROR`

`INTERNAL_ERROR`

## Authentication

외부 API는 api-gateway를 통해 호출한다.

api-gateway는 JWT를 검증하고 `X-User-Id`, `X-User-Role`, `X-Trace-Id`를 하위 서비스에 전달한다.

하위 서비스는 전달받은 사용자 헤더를 기준으로 권한을 확인한다.

내부 API는 `/internal/**` 경로로 분리한다.

## Idempotency

충전, 송금, 보상 지급은 멱등해야 한다.

각 거래 테이블은 `idempotency_key`와 `request_hash`를 저장한다.

같은 멱등키와 같은 요청 본문이면 기존 결과를 반환한다.

같은 멱등키와 다른 요청 본문이면 409를 반환한다.

## Money

금액은 정수 원화 단위로 저장한다.

음수 금액은 허용하지 않는다.

잔액 변경은 wallet-service만 수행한다.

동시성 제어는 wallet row의 optimistic locking 또는 조건부 update로 처리한다.

## Logging

모든 요청은 `traceId`를 로그에 남긴다.

비밀번호, 계좌 원문, 토큰은 로그에 남기지 않는다.

서비스 간 호출 실패는 대상 서비스명, 요청 ID, 실패 사유를 남긴다.

## Tests

공통 에러 응답 형식

JWT 인증 실패

권한 부족

멱등 재시도

멱등 충돌

잔액 동시성
