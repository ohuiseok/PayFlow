# 03. User Service

`user-service`는 최소 인증 서비스다.

결제 포트폴리오의 핵심은 아니므로 과하게 키우지 않는다.

## 목표

구현할 기능:

```text
회원가입
로그인
JWT 발급
사용자 조회
```

구현하지 않을 기능:

```text
Refresh Token
OAuth
복잡한 권한
KYC 상세 심사
비밀번호 재설정
이메일 인증
```

## API

### 회원가입

```http
POST /users
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password1234",
  "name": "홍길동"
}
```

응답:

```json
{
  "userId": 1,
  "email": "user@example.com",
  "name": "홍길동",
  "status": "ACTIVE"
}
```

### 로그인

```http
POST /users/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password1234"
}
```

응답:

```json
{
  "accessToken": "...",
  "tokenType": "Bearer",
  "expiresIn": 86400000
}
```

### 사용자 조회

```http
GET /users/{userId}
Authorization: Bearer {token}
```

응답:

```json
{
  "userId": 1,
  "email": "user@example.com",
  "name": "홍길동",
  "status": "ACTIVE"
}
```

## 엔티티

```java
User
- id
- email
- password
- name
- status
- createdAt
- updatedAt
```

UserStatus:

```text
ACTIVE
LOCKED
WITHDRAWN
```

## 구현 순서

1. `User` 엔티티 작성
2. `UserStatus` enum 작성
3. `UserRepository` 작성
4. `PasswordEncoder` 설정
5. `JwtTokenProvider` 작성
6. `UserService` 작성
7. `UserController` 작성
8. `GlobalExceptionHandler` 작성
9. 테스트 작성

## 검증 규칙

회원가입:

```text
email은 필수
email은 unique
password는 최소 8자
name은 필수
```

로그인:

```text
email이 없으면 실패
password 불일치 실패
WITHDRAWN 사용자는 로그인 실패
```

## JWT Claims

최소 claim:

```text
sub: userId
email
iat
exp
```

권장:

```text
role: USER
```

## 테스트

필수 테스트:

```text
회원가입 성공
중복 이메일 실패
비밀번호 암호화 확인
로그인 성공
로그인 실패
JWT subject에 userId 포함
사용자 조회 성공
없는 사용자 조회 실패
```

