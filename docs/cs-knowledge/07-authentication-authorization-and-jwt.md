# Authentication Authorization And JWT

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

## 핵심 개념

Authentication은 사용자가 누구인지 확인하는 인증이고, Authorization은 그 사용자가 어떤 행동을 할 수 있는지 판단하는 인가다.

JWT는 인증 결과를 서명된 토큰 형태로 전달하는 방식이다. 서버는 토큰의 서명을 검증해 사용자의 식별자와 권한 정보를 확인할 수 있다.

### 인증과 인가의 차이

인증과 인가는 서로 다른 책임을 가진다.

```text
인증 (Authentication): 신원 확인
  - 이 요청이 누구로부터 왔는가
  - JWT 서명 검증, 세션 확인, API 키 검증
  - 실패 시 401 Unauthorized 반환

인가 (Authorization): 권한 확인
  - 이 신원이 이 리소스에 접근할 수 있는가
  - 역할(Role), 소유권, 정책 기반 제어
  - 실패 시 403 Forbidden 반환
```

중요한 점은 인증이 성공해도 인가는 별도로 수행해야 한다는 것이다. 로그인한 사용자라도 다른 사람의 지갑에 접근하거나 관리자 API를 호출할 수 없어야 한다.

### JWT의 구조와 동작 원리

JWT(JSON Web Token)는 세 부분으로 구성된다.

```text
Header.Payload.Signature

Header: 알고리즘과 토큰 타입
{
  "alg": "HS256",
  "typ": "JWT"
}

Payload: 클레임(사용자 정보, 권한, 만료 시간)
{
  "sub": "user-123",
  "roles": ["USER"],
  "iat": 1700000000,
  "exp": 1700003600
}

Signature: Header + Payload를 서명 키로 서명한 값
HMACSHA256(base64(header) + "." + base64(payload), secret)
```

서버는 Signature를 검증해 토큰이 위조되지 않았음을 확인한다. Payload는 Base64로 인코딩되어 있을 뿐 암호화되지 않으므로 누구나 읽을 수 있다. 따라서 Payload에 민감한 정보(비밀번호, 개인정보)를 담으면 안 된다.

JWT는 Stateless하다. 서버가 토큰 정보를 DB에 저장하지 않고 서명 검증만으로 인증한다. 이 때문에 발급 후 서버 측에서 특정 토큰만 무효화하기 어렵다. 로그아웃 처리나 강제 토큰 만료가 필요하다면 Redis에 블랙리스트를 두거나, 짧은 만료 시간 + Refresh Token 전략을 사용해야 한다.

### Access Token과 Refresh Token 패턴

```text
Access Token
  - 짧은 만료 시간 (15분 ~ 1시간)
  - API 호출마다 헤더에 포함
  - 탈취되더라도 피해 최소화

Refresh Token
  - 긴 만료 시간 (7일 ~ 30일)
  - Access Token 재발급에만 사용
  - HttpOnly Cookie나 안전한 저장소에 보관
  - 사용 이력 DB에 저장하여 무효화 가능

갱신 흐름:
  1. 클라이언트가 Access Token으로 API 호출
  2. 서버가 401 반환 (토큰 만료)
  3. 클라이언트가 Refresh Token으로 /auth/refresh 호출
  4. 서버가 Refresh Token 유효성 확인 후 새 Access Token 발급
```

결제 API에서는 Access Token을 짧게 유지하는 것이 중요하다. 토큰이 탈취되더라도 피해 기간을 최소화할 수 있다.

### 인가 모델의 종류

**RBAC (Role-Based Access Control)**

역할 기반 접근 제어다. 사용자에게 역할을 부여하고, 역할에 따라 접근 권한을 제어한다.

```text
USER 역할: 자신의 지갑 조회, 송금
ADMIN 역할: 모든 지갑 조회, 강제 취소
BATCH 역할: 정산 배치 실행
```

단순하고 이해하기 쉽지만 세밀한 제어가 어렵다.

**리소스 소유권 기반 인가**

RBAC만으로는 "USER 역할을 가진 사람이 다른 USER의 지갑에 접근하는 것"을 막기 어렵다. 이를 위해 리소스 소유권 검증이 추가로 필요하다.

```java
// 잘못된 예: 역할만 확인
@PreAuthorize("hasRole('USER')")
public WalletResponse getWallet(Long walletId) {
    return walletRepository.findById(walletId);
}

// 올바른 예: 역할 + 소유권 확인
@PreAuthorize("hasRole('USER')")
public WalletResponse getWallet(Long walletId, Long requestUserId) {
    Wallet wallet = walletRepository.findById(walletId);
    if (!wallet.getOwnerId().equals(requestUserId)) {
        throw new ForbiddenException("해당 지갑에 접근 권한이 없습니다");
    }
    return toResponse(wallet);
}
```

### 내부 서비스 간 신뢰 경계

MSA에서 API Gateway가 JWT를 검증한 후 내부 서비스로 사용자 정보를 전달하는 방식은 두 가지다.

**헤더 전달 방식**

```text
Gateway -> transfer-service
  X-User-Id: user-123
  X-User-Roles: USER
```

이 방식에서 중요한 것은 외부에서 들어온 `X-User-Id` 헤더를 Gateway가 반드시 제거하고 다시 써야 한다는 것이다. 그렇지 않으면 악의적인 클라이언트가 `X-User-Id` 헤더를 직접 조작해 다른 사용자인 척할 수 있다.

```text
나쁜 흐름:
  클라이언트 -> Gateway -> transfer-service
  클라이언트가 X-User-Id: admin-999 헤더를 포함해서 보냄
  Gateway가 이 헤더를 그대로 전달하면 transfer-service가 admin-999로 처리

올바른 흐름:
  클라이언트 -> Gateway
  Gateway가 JWT를 검증하고 실제 userId 추출
  Gateway가 X-User-Id 헤더를 덮어씀
  Gateway -> transfer-service (X-User-Id: 실제userId)
```

### 흔한 오해와 함정

JWT를 쓰면 보안이 해결된다는 오해가 있다. JWT는 인증 메커니즘이지 보안 전체를 책임지지 않는다. 서명이 검증된 JWT라도 그 안의 userId로 다른 사람의 리소스에 접근하는 것은 인가 로직이 막아야 한다.

또 다른 함정은 JWT를 Local Storage에 저장하는 것이다. Local Storage는 XSS(Cross-Site Scripting) 공격으로 탈취될 수 있다. Access Token은 메모리에, Refresh Token은 HttpOnly Cookie에 저장하는 방식이 더 안전하다.

JWT Secret 키를 코드에 하드코딩하면 Git에 올라가는 순간 모든 토큰이 위조 가능해진다. Secret은 환경 변수나 Secret 저장소로 관리해야 한다.

### 성능과 trade-off 관점

JWT는 Stateless하기 때문에 서버가 DB나 Redis를 조회하지 않고 서명 검증만으로 인증할 수 있다. 이는 수평 확장에 유리하다.

반면 토큰 무효화가 어렵다는 단점이 있다. 사용자가 로그아웃해도 만료 전 토큰은 여전히 유효하다. 이를 해결하려면 Redis 블랙리스트를 두어야 하는데, 그러면 Stateless 장점이 줄어든다. 보안 요구 수준에 따라 단기 Access Token + Redis 블랙리스트 조합을 선택할 수 있다.

## PayFlow 연결

PayFlow에서는 API Gateway가 JWT를 검증하고 내부 서비스로 사용자 정보를 전달한다. 내부 서비스는 Gateway가 전달한 `X-User-Id` 같은 헤더를 기준으로 요청자의 소유권을 확인한다.

송금 요청에서는 인증된 사용자가 실제 송신 지갑의 소유자인지 반드시 확인해야 한다. 인증만 되어 있고 인가가 빠지면 다른 사람의 지갑으로 송금 요청을 만들 수 있다.

## 실무 포인트

- 인증과 인가를 구분한다.
- 외부에서 들어온 `X-User-Id` 헤더는 Gateway에서 제거하고 다시 만들어야 한다.
- JWT 만료 시간을 짧게 유지한다.
- 내부 서비스도 소유권 검증을 해야 한다.
- 관리자 API와 사용자 API를 분리한다.

## 체크 질문

- 인증과 인가의 차이는 무엇인가
- Gateway에서 JWT를 검증해도 내부 서비스에서 소유권 검증이 필요한 이유는 무엇인가
- 외부 요청의 `X-User-Id` 헤더를 그대로 믿으면 어떤 문제가 생기는가

## 실무 설계 참고

### 대표 장애 시나리오

인증된 사용자가 요청 body의 다른 walletId를 넣어 타인의 지갑으로 송금한다.

### 잘못된 구현 예시

~~~text
JWT 검증만 하면 끝이라고 보고 리소스 소유권 검증을 생략한다.
~~~

### 좋은 구현 예시

~~~text
Gateway에서 인증하고, 도메인 서비스에서 사용자와 지갑 소유권을 다시 검증한다.
~~~

### 대안과 선택 이유

Gateway에서 한 번만 보안을 처리하는 방식도 있지만, 결제 시스템에서는 단일 방어선이 뚫렸을 때 피해가 크다. PayFlow는 Gateway 인증, 서비스 내부 인가, 입력 검증, 로그 마스킹, Secret 분리를 겹겹이 두는 방식이 더 안전하다.

### PayFlow에서 확인할 위치

api-gateway 인증 필터, user-service, wallet-service 소유권 검증, transfer-service 송금 요청 처리

### 면접에서 설명하기

인증은 누구인지 확인하는 것이고 인가는 그 사람이 이 지갑을 쓸 수 있는지 확인하는 것이다.

### 관련 문서

08, 09, 10, 32

## 질문의 질문으로 더 깊게 보기

### 이 문서에서 잡아야 할 CS 감각

이 구간의 핵심은 API가 단순한 입구가 아니라 시스템의 계약이자 보안 경계라는 점이다. 누가 호출할 수 있는지, 어떤 입력을 믿을 수 있는지, 실패를 어떤 형태로 돌려주는지가 서비스 전체의 안정성을 결정한다.

### 질문의 질문

아래 질문들은 답을 외우기 위한 질문이 아니다. 하나씩 답하면서 "왜 그런 설계가 필요한지"를 설명하는 연습을 하기 위한 질문이다.

- 이 요청에서 서버가 믿어도 되는 값과 믿으면 안 되는 값은 무엇인가?
- 인증된 사용자라는 사실만으로 이 리소스에 접근해도 되는가?
- 로그, 에러 응답, 헤더 중 어디로 민감 정보가 새어나갈 수 있는가?
- 이 개념을 적용하면 어떤 문제가 해결되지만, 어떤 비용이나 복잡도가 새로 생기는가?
- 장애가 발생했을 때 이 개념은 문제를 예방하는가, 감지하는가, 복구하는가?
- PayFlow 코드에서 이 개념을 확인하려면 어떤 서비스, 테이블, 로그, 테스트를 봐야 하는가?

### 답안 예시

<details>
<summary>답안 예시 펼치기</summary>

#### 핵심 답변

Authentication Authorization And JWT 개념은 PayFlow에서 다음 이유로 중요하다.

- API와 보안 개념은 PayFlow에서 외부 요청이 내부 금전 명령으로 바뀌는 경계를 안전하게 만들기 위해 필요하다.
- Gateway가 인증 정보를 검증하지만, 실제 리소스 소유권의 진실은 각 도메인 서비스의 DB에 있다.
- 사용자가 보낸 userId나 walletId를 그대로 믿으면 다른 사람의 지갑에 접근하거나 금액을 조작할 수 있다.
- JWT 검증, 소유권 확인, 입력 검증, 내부 헤더 재생성, 민감 정보 마스킹, Secret 분리로 방어한다.
- 운영에서는 인증 실패율, 인가 실패율, 4xx 비율, 비정상 요청 패턴, 민감 정보 로그 노출 여부를 확인한다.

#### 질문에 답하는 방식

좋은 답변은 용어 정의에서 멈추지 않고 다음 순서로 이어져야 한다.

1. 어떤 데이터나 요청을 보호하려는 개념인지 말한다.
2. 그 데이터의 진실의 원천이 어느 서비스에 있는지 말한다.
3. 장애, 중복, 동시성, 지연 중 어떤 상황에서 문제가 생기는지 설명한다.
4. 코드 레벨의 방어 수단과 운영 레벨의 확인 수단을 함께 말한다.

#### PayFlow 예시 답변

```text
Authentication Authorization And JWT 개념은 PayFlow에서 사용자가 누구인지와 그 사용자가 해당 지갑을 사용할 수 있는지를 분리해서 검증하기 위해 필요하다.
이 개념이 없으면 로그인한 사용자가 다른 사람의 walletId로 송금 요청을 만들어 권한을 우회할 수 있다.
그래서 코드에서는 Gateway에서 JWT를 검증하고 내부 서비스에서 지갑 소유권과 요청자 ID를 다시 확인하며,
운영에서는 401/403 비율, 소유권 검증 실패, 위조 헤더 차단 로그를 확인해야 한다.
```

#### 더 생각해볼 점

이 답안은 하나의 예시다. 실제 설계에서는 성능, 복잡도, 장애 복구 난이도 사이의 trade-off를 함께 봐야 한다. 특히 PayFlow처럼 돈을 다루는 시스템에서는 빠른 성공보다 명확한 실패, 추적 가능한 상태, 재처리 가능한 구조가 더 중요하다.

</details>

### PayFlow에 대입해보기

1. 이 개념이 가장 직접적으로 연결되는 PayFlow 서비스 하나를 고른다.
2. 그 서비스가 소유한 데이터의 "진실의 원천"이 무엇인지 적어본다.
3. 동시에 두 요청이 들어오거나, 네트워크가 끊기거나, 프로세스가 죽는 상황을 가정한다.
4. 그 상황에서 데이터가 깨지지 않으려면 어떤 제약조건, 트랜잭션, 락, 이벤트, 재처리 장치가 필요한지 설명한다.
5. 마지막으로 운영자가 문제를 발견할 수 있는 로그나 지표가 무엇인지 적어본다.

### 설명 연습

다음 문장을 자기 말로 완성해보자.

```text
Authentication Authorization And JWT 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.

