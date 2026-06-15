# 03. User Service

user-service는 인증과 사용자 기본 정보를 담당한다.

## Scope

회원 가입

로그인

내 정보 조회

사용자 역할 조회

## APIs

### POST /auth/signup

요청:

`email`, `password`, `name`, `role`

처리:

1. email 중복을 확인한다.
2. password를 해시한다.
3. `users`를 생성한다.
4. wallet-service에 지갑 생성을 요청한다.

응답:

`userId`, `email`, `name`, `role`

### POST /auth/login

요청:

`email`, `password`

처리:

1. 사용자를 조회한다.
2. 비밀번호를 검증한다.
3. JWT access token을 발급한다.

응답:

`accessToken`, `user`

### GET /users/me

인증된 사용자의 기본 정보를 반환한다.

### GET /internal/users/{userId}

서비스 간 사용자 검증에 사용한다.

## Status

`ACTIVE`

`LOCKED`

`WITHDRAWN`

## Validation

email은 unique다.

password는 최소 길이와 복잡도 정책을 적용한다.

role은 `PARENT`, `CHILD`만 허용한다.

탈퇴 또는 잠긴 사용자는 로그인할 수 없다.

## Tests

회원 가입 성공

중복 email 가입 실패

로그인 성공

비밀번호 오류 로그인 실패

잠긴 사용자 로그인 실패

내 정보 조회 성공

내부 사용자 조회 성공
