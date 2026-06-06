# 03. User Service

`user-service`는 인증, 사용자 역할, 프로필, 기본 설정의 진실을 가진다.

결제 포트폴리오의 핵심은 아니므로 과하게 키우지 않되, `sample-react` 목업과 `docs/api-spec.md`에서 요구하는 역할 기반 화면 진입과 설정 API는 지원한다.

MVP에서는 기본 회원가입, 로그인, JWT 발급, 사용자 조회를 우선한다.
역할 선택, 프로필 수정, 알림 설정, 로그아웃 정책 고도화는 보강/2차 범위로 둔다.

## 목표

구현할 기능:

```text
회원가입
로그인
JWT 발급
사용자 조회

보강/2차:
역할 선택
프로필 조회/수정
알림 설정 조회/수정
로그아웃
```

구현하지 않을 기능:

```text
Refresh Token
OAuth
복잡한 권한
KYC 상세 심사
비밀번호 재설정
이메일 인증
복잡한 관리자 권한
```

## API

### 회원가입

```http
POST /users
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password1234",
  "name": "홍길동",
  "role": "PARENT"
}
```

응답:

```json
{
  "userId": 1,
  "email": "user@example.com",
  "name": "홍길동",
  "role": "PARENT",
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
  "role": "PARENT",
  "status": "ACTIVE"
}
```

### 프로필 조회

```http
GET /settings/profile
X-User-Id: 1
```

응답:

```json
{
  "userId": 1,
  "email": "user@example.com",
  "name": "홍길동",
  "role": "PARENT",
  "familyCount": 2,
  "dummyDataMode": false
}
```

### 프로필 수정

```http
PATCH /settings/profile
X-User-Id: 1
Content-Type: application/json

{
  "name": "홍길동",
  "email": "new@example.com"
}
```

### 알림 설정 조회/수정

```http
GET /settings/notification-preferences
PATCH /settings/notification-preferences
X-User-Id: 1
```

### 로그아웃

```http
POST /users/logout
X-User-Id: 1
```

JWT 블랙리스트를 구현하지 않는 초기 정책이면 클라이언트 토큰 삭제로 처리하고, API는 2차 구현으로 둔다.

## 엔티티

```java
User
- id
- email
- password
- name
- role
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

UserRole:

```text
PARENT
CHILD
```

NotificationPreference:

```text
- id
- userId
- missionSubmitted
- missionApproved
- missionRejected
- chargeCompleted
- createdAt
- updatedAt
```

## 구현 순서

1. `User` 엔티티 작성
2. `UserStatus` enum 작성
3. `UserRole` enum 작성
4. `NotificationPreference` 엔티티 작성
5. `UserRepository` 작성
6. `NotificationPreferenceRepository` 작성
7. `PasswordEncoder` 설정
8. `JwtTokenProvider` 작성
9. `UserService` 작성
10. `SettingsService` 작성
11. `UserController` 작성
12. `SettingsController` 작성
13. `GlobalExceptionHandler` 작성
14. 테스트 작성

## 검증 규칙

회원가입:

```text
email은 필수
email은 unique
password는 최소 8자
name은 필수
role은 PARENT 또는 CHILD
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

최종 claim:

```text
role: PARENT 또는 CHILD
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
역할 포함 회원가입 성공
프로필 조회/수정 성공
알림 설정 조회/수정 성공
```
