# 12. Gateway And Security

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

api-gateway는 외부 요청의 단일 진입점이다.

## Scope

JWT 검증

사용자 헤더 전달

MVP API 라우팅

외부 신뢰 헤더 제거와 내부 Gateway secret 주입

## Routes

| Path | Target |
| --- | --- |
| `/api/auth/**` | user-service |
| `/api/users/**` | user-service |
| `/api/wallets/**` | wallet-service |
| `/api/bank/**` | banking-service |
| `/api/transfers/**` | transfer-service |
| `/api/ledgers/**` | ledger-service |
| `/api/settlements/**` | settlement-service |
| `/api/families/**` | reward-service |
| `/api/missions/**` | reward-service |
| `/api/cashbook/**` | reward-service |

## Public Paths

`POST /api/users`

`POST /api/users/login`

`/actuator/health`

## Headers

하위 서비스로 전달:

`X-User-Id`

`X-User-Role`

`X-User-Phone-Number`

`X-Internal-Request`

`X-Gateway-Secret`

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

