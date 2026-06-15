# API Gateway And Reverse Proxy

## 핵심 개념

### Reverse Proxy의 기본 동작 원리

Reverse Proxy는 클라이언트와 백엔드 서버 사이에 위치해 클라이언트의 요청을 대신 받아 내부 서버로 전달하고, 응답을 다시 클라이언트에게 돌려주는 서버다. 클라이언트는 내부 서버의 실제 주소를 알 수 없다.

```text
클라이언트
   |
   | HTTPS 443
   v
[Nginx / Reverse Proxy]
   |
   +---> transfer-service:8080
   +---> wallet-service:8081
   +---> user-service:8082
```

Reverse Proxy가 없으면 내부 서비스 포트를 모두 외부에 노출해야 한다. 이는 보안 취약점이 되고, 서비스 주소가 바뀔 때마다 클라이언트도 함께 바꿔야 하는 결합 문제가 생긴다.

### API Gateway란 무엇인가

API Gateway는 Reverse Proxy에 더해 다음 기능을 포함하는 컴포넌트다.

- 라우팅: 요청 경로에 따라 적절한 내부 서비스로 전달
- 인증/인가: JWT 검증, API Key 확인, 권한 확인
- Rate Limiting: 요청 수 제한
- 로드 밸런싱: 여러 인스턴스 중 하나로 분산
- 로깅 및 추적: 모든 요청에 correlation ID 부여
- 요청/응답 변환: 헤더 추가, 형식 변환
- Circuit Breaker: 장애 서비스로의 요청 차단

Nginx, Kong, Spring Cloud Gateway, AWS API Gateway가 대표적인 구현체다.

### JWT 검증과 헤더 신뢰 경계

API Gateway에서 JWT를 검증하면 내부 서비스는 Gateway가 검증한 사용자 정보를 신뢰할 수 있다. 이때 Gateway는 JWT를 파싱해 사용자 ID, 역할 등을 내부 헤더로 변환해 전달한다.

```text
클라이언트 요청
  Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

Gateway가 JWT 검증 후 내부 서비스로 전달
  X-User-Id: user-123
  X-User-Role: CUSTOMER
  Authorization: [제거 또는 내부 토큰으로 교체]
```

중요한 보안 원칙이 있다. **외부에서 들어오는 X-User-Id 같은 헤더는 반드시 Gateway에서 제거하고, Gateway가 새로 넣어야 한다.** 그렇지 않으면 악의적인 클라이언트가 X-User-Id 헤더를 직접 조작해 다른 사용자인 척 요청할 수 있다.

```java
// Spring Cloud Gateway 예시: 외부 헤더 제거 후 새 값 삽입
.filters(f -> f
    .removeRequestHeader("X-User-Id")      // 외부 헤더 제거
    .addRequestHeader("X-User-Id", userId) // Gateway가 검증한 값 삽입
)
```

### 내부 서비스의 인가는 별도로 필요하다

Gateway에서 인증을 처리했다고 해서 내부 서비스의 인가가 필요 없어지는 것은 아니다.

예를 들어 사용자 A가 사용자 B의 지갑 정보를 조회하려는 요청을 보낸다고 하자. Gateway는 "이 사람은 유효한 사용자다"까지만 검증한다. "이 사람이 이 데이터에 접근할 권한이 있는가"는 wallet-service가 직접 확인해야 한다.

```java
// wallet-service 내부 인가 예시
@GetMapping("/wallets/{walletId}")
public WalletResponse getWallet(
    @PathVariable String walletId,
    @RequestHeader("X-User-Id") String requestingUserId
) {
    Wallet wallet = walletRepository.findById(walletId);
    // 내부 인가: 요청자와 지갑 소유자가 같은지 확인
    if (!wallet.getOwnerId().equals(requestingUserId)) {
        throw new AccessDeniedException("다른 사용자의 지갑에 접근할 수 없습니다");
    }
    return wallet.toResponse();
}
```

### 라우팅 전략

API Gateway의 라우팅은 다양한 기준으로 설정할 수 있다.

**경로 기반 라우팅**

```yaml
# Spring Cloud Gateway 예시
spring:
  cloud:
    gateway:
      routes:
        - id: transfer-service
          uri: http://transfer-service:8080
          predicates:
            - Path=/transfers/**
        - id: wallet-service
          uri: http://wallet-service:8081
          predicates:
            - Path=/wallets/**
```

**헤더 기반 라우팅**: 특정 헤더 값에 따라 라우팅. A/B 테스트나 버전 분기에 사용.

**가중치 기반 라우팅**: 트래픽의 일부를 새 버전으로 점진적으로 이동.

### Gateway 단일 장애점 문제

API Gateway는 모든 외부 요청이 통과하는 단일 진입점이므로, Gateway가 다운되면 전체 서비스가 중단된다. 이를 방지하기 위해 다음 대책이 필요하다.

- 다중 인스턴스 배포: Gateway를 최소 2개 이상 실행하고 로드 밸런서를 앞에 둔다.
- Circuit Breaker: 특정 내부 서비스 장애가 Gateway 전체로 전파되지 않게 한다.
- 타임아웃 설정: 내부 서비스 응답이 느릴 때 무한 대기하지 않도록 한다.
- 헬스 체크: 각 내부 서비스의 상태를 주기적으로 확인하고 다운된 서비스로 라우팅하지 않는다.

### 흔한 오해와 함정

**Gateway에 비즈니스 로직을 넣는 실수**

Gateway는 공통 정책을 적용하는 곳이지, 비즈니스 규칙을 처리하는 곳이 아니다. 예를 들어 "잔액이 충분한지 확인"은 wallet-service가 해야 할 일이다. Gateway에 이런 로직이 들어오면 Gateway가 도메인 지식을 가진 거대한 단일 장애점이 된다.

**내부 포트 외부 노출**

Docker Compose나 쿠버네티스에서 내부 서비스 포트를 실수로 외부에 노출하면 Gateway를 우회한 직접 접근이 가능해진다. 내부 서비스는 사설 네트워크 안에서만 접근 가능하게 해야 한다.

```yaml
# docker-compose 예시: wallet-service는 외부 포트 미노출
services:
  wallet-service:
    image: wallet-service
    # ports: - "8081:8081"  # 이 줄이 있으면 외부에서 직접 접근 가능
    networks:
      - internal  # 내부 네트워크에만 연결

  api-gateway:
    ports:
      - "8080:8080"  # Gateway만 외부 포트 노출
    networks:
      - internal
      - external
```

## PayFlow 연결

PayFlow에서는 Nginx가 앞단 Reverse Proxy 역할을 하고, API Gateway가 서비스 라우팅과 JWT 검증을 담당한다.

외부 클라이언트는 내부 서비스 주소를 알 필요가 없다. Gateway는 `/users`, `/wallets`, `/transfers` 같은 경로를 적절한 서비스로 전달한다.

## 실무 포인트

- Gateway에서 인증을 검증하더라도 내부 서비스의 인가는 필요하다.
- 외부에서 들어온 사용자 헤더는 제거하고 신뢰 가능한 값으로 다시 넣는다.
- Gateway에 너무 많은 비즈니스 로직을 넣지 않는다.
- 공통 에러 형식과 추적 ID를 적용하기 좋다.

## 체크 질문

- Reverse Proxy와 API Gateway의 차이는 무엇인가
- Gateway에 비즈니스 로직을 많이 넣으면 어떤 문제가 생기는가
- 내부 서비스가 Gateway를 통과한 요청만 신뢰하게 하려면 무엇이 필요한가

## 실무 설계 참고

### 대표 장애 시나리오

외부 사용자가 내부 서비스 포트로 직접 접근해 Gateway 인증을 우회한다.

### 잘못된 구현 예시

~~~text
Gateway를 단순 라우터로만 보고 신뢰 경계를 설계하지 않는다.
~~~

### 좋은 구현 예시

~~~text
외부 노출은 Nginx/Gateway로 제한하고 내부 서비스는 사설 네트워크에서만 접근하게 한다.
~~~

### 대안과 선택 이유

처음에는 모든 서비스를 느슨하게 열어두고 편하게 호출하는 방식이 빠르지만, 운영에 가까워질수록 보안 경계와 설정 혼선이 문제가 된다. PayFlow는 Compose 네트워크, Gateway, 환경 설정 분리, Rate Limit으로 경계를 명확히 하는 방식이 더 낫다.

### PayFlow에서 확인할 위치

api-gateway, nginx.conf, docker-compose ports, service routes

### 면접에서 설명하기

API Gateway는 입구다. 입구에서 인증, 헤더 정제, 라우팅 정책을 일관되게 적용한다.

### 관련 문서

05, 07, 31, 33

## 질문의 질문으로 더 깊게 보기

### 이 문서에서 잡아야 할 CS 감각

이 구간의 핵심은 서비스를 나누면 코드만 나뉘는 것이 아니라 네트워크, 배포, 설정, 라우팅, 장애 지점도 함께 늘어난다는 점이다. MSA의 장점은 경계가 명확할 때만 살아난다.

### 질문의 질문

아래 질문들은 답을 외우기 위한 질문이 아니다. 하나씩 답하면서 "왜 그런 설계가 필요한지"를 설명하는 연습을 하기 위한 질문이다.

- API Gateway And Reverse Proxy 개념이 없다면 PayFlow에서 가장 먼저 어떤 장애가 생기는가?
- 이 개념은 정확성, 성능, 보안, 운영성 중 무엇을 가장 크게 개선하는가?
- 반대로 이 개념을 잘못 적용하면 어떤 복잡도가 추가되는가?
- 이 개념을 적용하면 어떤 문제가 해결되지만, 어떤 비용이나 복잡도가 새로 생기는가?
- 장애가 발생했을 때 이 개념은 문제를 예방하는가, 감지하는가, 복구하는가?
- PayFlow 코드에서 이 개념을 확인하려면 어떤 서비스, 테이블, 로그, 테스트를 봐야 하는가?

### 답안 예시

<details>
<summary>답안 예시 펼치기</summary>

#### 핵심 답변

API Gateway And Reverse Proxy 개념은 PayFlow에서 다음 이유로 중요하다.

- Docker, MSA, Gateway 개념은 PayFlow 서비스를 어떤 경계로 나누고 어떻게 안전하게 연결할지 결정하기 위해 필요하다.
- 서비스별 API 계약, Docker 네트워크, Gateway 라우팅, 각 서비스 DB가 기준이다.
- 서비스 주소, 설정, 라우팅, 내부 포트 노출이 잘못되면 요청이 실패하거나 보안 경계가 무너진다.
- 서비스별 책임 분리, 내부 DB 직접 접근 금지, 환경 설정 분리, Gateway 정책, Rate Limit으로 방어한다.
- 운영에서는 서비스별 health, 라우팅 실패율, 429 응답, 컨테이너 재시작 횟수, 설정 오류 로그를 본다.

#### 질문에 답하는 방식

좋은 답변은 용어 정의에서 멈추지 않고 다음 순서로 이어져야 한다.

1. 어떤 데이터나 요청을 보호하려는 개념인지 말한다.
2. 그 데이터의 진실의 원천이 어느 서비스에 있는지 말한다.
3. 장애, 중복, 동시성, 지연 중 어떤 상황에서 문제가 생기는지 설명한다.
4. 코드 레벨의 방어 수단과 운영 레벨의 확인 수단을 함께 말한다.

#### PayFlow 예시 답변

```text
API Gateway And Reverse Proxy 개념은 PayFlow에서 외부 요청이 Gateway를 통해 인증과 라우팅 정책을 거쳐 내부 서비스로 들어가게 하기 위해 필요하다.
이 개념이 없으면 사용자가 내부 wallet-service 출금 API를 직접 호출해 Gateway 인증과 헤더 정제를 우회할 수 있다.
그래서 코드에서는 Nginx/Gateway 라우팅, JWT 검증, 외부 헤더 제거, 내부 포트 비노출을 적용하고,
운영에서는 Gateway 우회 시도, 라우팅 실패, 인증 실패 로그를 확인해야 한다.
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
API Gateway And Reverse Proxy 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.
