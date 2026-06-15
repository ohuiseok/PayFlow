# 12. Gateway And Security

api-gateway는 외부 요청의 단일 진입점이다.

## Scope

JWT 검증

사용자 헤더 전달

MVP API 라우팅

공통 traceId 생성

## Routes

| Path | Target |
| --- | --- |
| `/api/auth/**` | user-service |
| `/api/users/**` | user-service |
| `/api/wallets/**` | wallet-service |
| `/api/bank/**` | banking-service |
| `/api/transfers/**` | transfer-service |
| `/api/ledger/**` | ledger-service |
| `/api/families/**` | reward-service |
| `/api/missions/**` | reward-service |
| `/api/cashbook/**` | reward-service |

## Public Paths

`/api/auth/signup`

`/api/auth/login`

`/actuator/health`

## Headers

하위 서비스로 전달:

`X-User-Id`

`X-User-Role`

`X-Trace-Id`

## Security Rules

공개 경로 외에는 JWT가 필요하다.

사용자 역할 검증은 각 서비스에서 수행한다.

내부 API는 게이트웨이 외부에 노출하지 않는다.

민감 정보는 로그에 남기지 않는다.

## Tests

공개 경로 접근 성공

인증 없는 보호 경로 접근 실패

유효하지 않은 JWT 실패

사용자 헤더 전달

MVP route 라우팅 성공
