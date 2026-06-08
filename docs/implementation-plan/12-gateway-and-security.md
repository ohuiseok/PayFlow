# 12. Gateway And Security

이 문서는 API Gateway와 보안 구현 계획이다.

## 목표

Gateway 역할:

```text
외부 요청 단일 진입점
서비스별 라우팅
JWT 검증
공통 에러 응답
추후 rate limiting 확장
```

## 라우팅

현재 Eureka를 사용하지 않는다.

Docker Compose 서비스 이름으로 직접 라우팅한다.

```text
/api/users/**       -> http://user-service:8081
/api/wallets/**     -> http://wallet-service:8082
/api/credits/**     -> http://banking-service:8086
/api/bank/**        -> http://banking-service:8086
/api/transfers/**   -> http://transfer-service:8083
/api/families/**    -> http://reward-service:8087
/api/missions/**    -> http://reward-service:8087
/api/cashbook/**    -> http://reward-service:8087
/api/parent-history/** -> http://reward-service:8087
/api/notifications/** -> http://reward-service:8087
/api/files/**       -> http://reward-service:8087
/api/settings/**    -> http://user-service:8081
/api/ledgers/**     -> http://ledger-service:8084
/api/settlements/** -> http://settlement-service:8085
```

라우트 범위:

| 경로 | 범위 | 설명 |
|---|---|---|
| `/api/credits/**` | MVP | banking-service 공개 충전 API |
| `/api/bank/**` | legacy/내부 테스트 | 전환 기간에만 유지하거나 제거 |
| `/api/parent-history/**` | 보강/2차 | 부모 지급/정산 내역 |
| `/api/notifications/**` | 보강/2차 | 알림 목록/읽음 |
| `/api/files/**` | 보강/2차 | 미션 인증 사진 업로드 URL |
| `/api/settings/**` | 보강/2차 | 프로필/알림 설정 |
| `/api/settlements/**` | 보강/2차 | 정산 실행/조회 |

문서에서 API 예시는 서비스 내부 경로를 기준으로 쓴다.

```text
External: POST /api/transfers
Internal: POST /transfers
```

## 인증 제외 경로

```text
POST /api/users
POST /api/users/login
GET /actuator/health
```

## 인증 필요 경로

```text
/api/wallets/**
/api/credits/**
/api/bank/**
/api/transfers/**
/api/families/**
/api/missions/**
/api/cashbook/**
/api/parent-history/**
/api/notifications/**
/api/files/**
/api/settings/**
/api/ledgers/**
/api/settlements/**
```

`/api/bank/**`, `/api/parent-history/**`, `/api/notifications/**`, `/api/files/**`, `/api/settings/**`, `/api/settlements/**`는 등록하더라도 MVP 필수 구현 대상은 아니다.

## JWT 검증

Gateway에서 JWT를 검증한다.

검증 후 내부 서비스로 아래 헤더를 전달한다.

```text
X-User-Id
X-User-Phone-Number
X-User-Role
```

Gateway는 외부 요청에 포함된 사용자 헤더를 먼저 제거한다.

```text
X-User-Id
X-User-Phone-Number
X-User-Role
X-Internal-Request
X-Internal-Secret
```

그 다음 검증된 JWT claim으로 `X-User-*` 헤더를 다시 생성한다.

초기 내부 서비스는 이 헤더를 신뢰한다.

추후 개선:

```text
내부망에서만 서비스 접근 가능하도록 EC2 Security Group / Docker network 제한
서비스 간 shared secret header 추가
```

## 권한과 소유권

Gateway는 인증 여부를 판단하고, 실제 리소스 소유권은 각 서비스가 검증한다.

규칙:

```text
wallet-service는 wallet.userId와 X-User-Id 일치 여부를 확인한다.
transfer-service는 senderWalletId가 X-User-Id 사용자의 지갑인지 확인한다.
reward-service는 parentUserId/childUserId와 X-User-Id 일치 여부를 API별로 확인한다.
reward-service는 가족 연결, 미션, 캐시북 권한을 API별로 확인한다.
알림과 파일 업로드 URL 권한 검증은 해당 보강/2차 API를 구현할 때 추가한다.
banking-service는 크레딧 충전 계좌와 충전 요청의 소유권을 확인한다.
ledger-service 조회는 송금 당사자 지갑과 관련된 원장만 허용한다.
settlement-service API는 초기에는 내부/관리자 전용으로 둔다.
```

초기 관리자 권한이 없으면 아래 API는 외부 공개하지 않는다.

```text
수동 충전
내부 withdraw/deposit
정산 실행
장애 복구/재처리 API
```

## Rate Limiting

초기에는 구현하지 않아도 된다.

추후 Redis RateLimiter를 적용한다.

우선순위:

```text
1. 송금 요청 제한
2. 로그인 요청 제한
3. 전체 사용자별 제한
```

## 에러 코드

Gateway/security 주요 에러:

```text
UNAUTHORIZED
INVALID_TOKEN
TOKEN_EXPIRED
FORBIDDEN
RESOURCE_OWNER_MISMATCH
INTERNAL_HEADER_FORBIDDEN
```

## 구현 순서

1. Gateway route 설정 확인
2. JwtAuthenticationFilter 작성
3. 인증 제외 경로 설정
4. JWT 파싱 유틸 작성
5. 사용자 정보 헤더 전달
6. 에러 응답 통일
7. `docs/api-spec.md`의 모든 외부 경로 route 등록
8. 테스트 작성

## 테스트

필수 테스트:

```text
인증 제외 경로 통과
토큰 없는 요청 거부
잘못된 토큰 거부
정상 토큰 라우팅 성공
X-User-Id 헤더 전달
외부에서 보낸 X-User-Id 헤더 제거
외부에서 보낸 X-Internal-* 헤더 제거
타인 리소스 접근 시 403
없는 route 404
MVP 신규 경로 families/credits/missions/cashbook 라우팅 성공
```

보강/2차 테스트:

```text
parent-history route 라우팅 성공
notifications route 라우팅 성공
files route 라우팅 성공
settings route 라우팅 성공
settlements route 라우팅 성공
```
