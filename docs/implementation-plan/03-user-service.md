# 03. User Service

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

user-service는 인증과 사용자 기본 정보를 담당한다.

## Scope

회원 가입

로그인

내 정보 조회

사용자 역할 조회

## APIs

### POST /users

요청:

`phoneNumber`, `password`, `name`, `inviteCode`

처리:

1. 정규화한 phoneNumber 중복을 확인한다.
2. password를 해시한다.
3. 유효한 기관 초대 코드가 있으면 `PARENT`, 없으면 `CHILD` 역할을 결정한다.
4. `users`를 생성한다.
5. wallet-service에 지갑 생성을 요청한다.

응답:

`userId`, `phoneNumber`, `name`, `role`, `status`

### POST /users/login

요청:

`phoneNumber`, `password`

처리:

1. 사용자를 조회한다.
2. 비밀번호를 검증한다.
3. JWT access token을 발급한다.

응답:

`accessToken`, `tokenType`, `expiresIn`

### GET /users/me

인증된 사용자의 기본 정보를 반환한다.

### GET /internal/users/{userId}

서비스 간 사용자 검증에 사용한다.

## Status

`ACTIVE`

`LOCKED`

`WITHDRAWN`

## Validation

phoneNumber는 정규화 후 unique다.

password는 최소 길이와 복잡도 정책을 적용한다.

role은 클라이언트가 직접 지정하지 않고 기관 초대 코드로 결정한다.

탈퇴 또는 잠긴 사용자는 로그인할 수 없다.

## Tests

회원 가입 성공

중복 phoneNumber 가입 실패

로그인 성공

비밀번호 오류 로그인 실패

잠긴 사용자 로그인 실패

내 정보 조회 성공

내부 사용자 조회 성공

