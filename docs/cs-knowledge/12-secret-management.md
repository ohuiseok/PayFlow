# Secret Management

> 도메인 전환 안내: 현재 PayFlow는 **청년 정책 참여 미션 및 지원금 지급 플랫폼**으로 설명한다. 내부 구현 호환성을 위해 `PARENT`/`CHILD`, `/api/families`, `/api/missions`, `/api/cashbook`, `reward-service` 같은 명칭은 유지하지만, 문서와 발표에서는 각각 **기관 담당자**, **청년 참여자**, **참여자 연결**, **정책 미션**, **지원금 사용 내역**, **정책 미션/지원금 서비스**로 해석한다.

## 핵심 개념

Secret은 DB 비밀번호, JWT 서명 키, API 키, 인증서 개인키처럼 노출되면 안 되는 설정값이다. Secret Management는 이런 값을 안전하게 저장하고 배포하며 교체하는 방법이다.

소스 코드나 Git 저장소에 Secret을 직접 넣으면 안 된다. 한 번 커밋된 Secret은 삭제해도 기록에 남을 수 있다.

### Secret과 일반 설정값의 차이

모든 설정값이 Secret은 아니다. 구분 기준은 "노출됐을 때 시스템 보안이 직접 침해되는가"다.

```text
일반 설정값 (공개해도 무방):
  - 서버 포트 번호
  - 로그 레벨
  - 타임아웃 값
  - 환경 이름 (dev, staging, prod)

환경별 설정값 (공개 가능하지만 환경마다 다름):
  - DB 호스트, 포트
  - Kafka 브로커 주소
  - Redis 호스트

Secret (절대 공개하면 안 됨):
  - DB 비밀번호
  - JWT 서명 키
  - 외부 API 키 (카드사, 결제 게이트웨이)
  - 인증서 개인키
  - 암호화 키
  - OAuth Client Secret
```

### Git에서 Secret이 유출되는 경로

```text
의도적 커밋:
  application.yml, .env에 직접 Secret 값 작성
  README에 예시 코드로 실제 Secret 포함

실수에 의한 커밋:
  .env 파일을 .gitignore에 추가하기 전에 커밋
  로컬 설정 파일을 실수로 포함

기록에 남는 경우:
  Secret을 커밋했다가 삭제해도 git log에 남음
  git blame, git show로 과거 커밋 조회 가능
  GitHub에 push된 경우 GitHub가 검색 인덱스에 포함할 수 있음
```

Git 기록에서 Secret을 완전히 제거하려면 `git filter-branch`나 BFG Repo Cleaner 같은 도구가 필요하다. 하지만 이미 public으로 노출된 경우에는 Secret을 즉시 교체하는 것이 우선이다.

### Secret 저장과 배포 방법

**로컬 개발: .env 파일**

```bash
# .env (Git 제외 - .gitignore에 추가)
DB_PASSWORD=localpassword123
JWT_SECRET=localdevonly-not-secure

# .env.example (Git 포함 - 예시 값만)
DB_PASSWORD=your-db-password-here
JWT_SECRET=your-jwt-secret-here
```

**.env.example**은 팀원들이 어떤 환경 변수가 필요한지 알 수 있도록 키 목록과 형식 예시만 포함한다.

**운영 환경 옵션 1: 환경 변수 직접 주입**

```yaml
# docker-compose.yml
services:
  transfer-service:
    environment:
      - DB_PASSWORD=${DB_PASSWORD}
      - JWT_SECRET=${JWT_SECRET}
```

환경 변수는 `docker inspect`로 볼 수 있으므로 컨테이너 접근 권한 관리가 중요하다.

**운영 환경 옵션 2: AWS Secrets Manager / HashiCorp Vault**

```text
AWS Secrets Manager:
  - Secret을 AWS에 저장하고 IAM 권한으로 접근 제어
  - 애플리케이션 시작 시 Secret 조회
  - 자동 교체(Rotation) 지원
  - AWS SDK로 런타임 조회 가능

HashiCorp Vault:
  - 멀티 클라우드, 온프레미스 지원
  - 동적 Secret 생성 (각 요청마다 임시 DB 계정 생성)
  - 세밀한 접근 정책
  - 감사 로그
```

**쿠버네티스: Kubernetes Secrets**

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: payflow-secrets
type: Opaque
stringData:
  db-password: "실제비밀번호"
  jwt-secret: "실제키"
```

Kubernetes Secrets는 etcd에 저장되는데, 기본적으로 Base64 인코딩만 되어 있어 암호화되지 않는다. etcd 암호화를 추가로 설정하거나, External Secrets Operator로 Vault나 AWS Secrets Manager와 연동하는 것이 좋다.

### Secret 교체 전략

Secret은 주기적으로 교체해야 하며, 유출이 의심되면 즉시 교체해야 한다.

```text
DB 비밀번호 교체 절차:
  1. 새 비밀번호 생성
  2. DB에 새 비밀번호 설정 (구 비밀번호는 아직 유효)
  3. 애플리케이션 설정 업데이트 + 재배포
  4. DB에서 구 비밀번호 삭제
  다운타임 없이 교체 가능

JWT Secret 교체 절차:
  1. 새 Secret으로 새 토큰 발급
  2. 구 Secret은 아직 유효 (사용자가 로그인 중)
  3. 구 Secret의 토큰 만료 기간 대기 (또는 강제 로그아웃)
  4. 구 Secret 폐기
  -> JWT 만료 시간이 짧을수록 교체가 빠름
```

### Secret 유출 감지

GitHub는 코드에 일반적인 Secret 패턴이 포함되면 자동으로 감지해 알림을 보내는 기능이 있다. 또한 git-secrets, gitleaks 같은 도구를 CI 파이프라인에 추가해 커밋 전에 Secret을 검사할 수 있다.

```bash
# gitleaks 설치 후 검사
gitleaks detect --source . --verbose

# 커밋 전 훅으로 자동 검사
# .git/hooks/pre-commit에 추가
gitleaks protect --staged
```

### 흔한 오해와 함정

"이 프로젝트는 공개 저장소가 아니니 괜찮다"는 생각은 위험하다. 비공개 저장소도 팀원이 나가거나, 저장소가 공개로 전환되거나, 저장소 서비스 침해가 발생할 수 있다.

환경 변수도 완벽하지 않다. `printenv`, `docker inspect`, 애플리케이션 설정 노출 엔드포인트(`/actuator/env`)로 환경 변수가 노출될 수 있다. Spring Actuator는 운영 환경에서 적절한 보안 설정이 필요하다.

또한 로그에 환경 변수를 출력하는 코드도 위험하다. 애플리케이션 시작 시 모든 환경 변수를 로그로 남기는 경우 Secret이 로그 시스템에 저장된다.

### trade-off 관점

Secret 저장소를 쓰면 보안이 강화되지만 추가 인프라와 운영 비용이 생긴다. 소규모 프로젝트에서는 .env 파일 + .gitignore + 최소 권한 원칙으로 시작하고, 규모가 커지면 전용 Secret 저장소로 마이그레이션하는 방식이 현실적이다.

## PayFlow 연결

PayFlow에는 MySQL 비밀번호, Redis 정보, Kafka 설정, JWT secret, 배포 환경의 인증 정보가 필요하다. 로컬에서는 `.env`를 사용할 수 있지만, 운영에서는 권한이 통제되는 Secret 저장소를 사용하는 것이 좋다.

Docker Compose에서도 환경 변수로 주입하되, `.env.example`에는 실제 값이 아닌 예시 값만 둬야 한다.

## 실무 포인트

- 실제 Secret을 Git에 커밋하지 않는다.
- `.env.example`과 실제 `.env`를 분리한다.
- Secret은 주기적으로 교체할 수 있어야 한다.
- 로그에 환경 변수를 출력하지 않는다.
- 운영 권한을 최소화한다.

## 체크 질문

- Secret과 일반 설정값의 차이는 무엇인가
- JWT 서명 키가 유출되면 어떤 문제가 생기는가
- `.env.example`에는 어떤 값을 넣어야 하는가

## 실무 설계 참고

### 대표 장애 시나리오

JWT secret이나 DB 비밀번호가 Git에 커밋되어 토큰 위조와 DB 접근 위험이 생긴다.

### 잘못된 구현 예시

~~~text
환경별 secret을 application.yml이나 README에 직접 적는다.
~~~

### 좋은 구현 예시

~~~text
실제 secret은 환경 변수나 secret 저장소로 주입하고 예시 파일에는 더미 값만 둔다.
~~~

### 대안과 선택 이유

Gateway에서 한 번만 보안을 처리하는 방식도 있지만, 결제 시스템에서는 단일 방어선이 뚫렸을 때 피해가 크다. PayFlow는 Gateway 인증, 서비스 내부 인가, 입력 검증, 로그 마스킹, Secret 분리를 겹겹이 두는 방식이 더 안전하다.

### PayFlow에서 확인할 위치

.env, .env.example, application.yml, docker-compose.yml, 배포 설정

### 면접에서 설명하기

Secret은 설정값이지만 공개 가능한 설정값이 아니다. 노출되면 인증과 데이터 접근 경계가 무너진다.

### 관련 문서

07, 10, 30

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

Secret Management 개념은 PayFlow에서 다음 이유로 중요하다.

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
Secret Management 개념은 PayFlow에서 JWT 서명 키와 DB 비밀번호 같은 비밀값을 코드와 저장소에서 분리하기 위해 필요하다.
이 개념이 없으면 Git에 올라간 JWT secret으로 공격자가 유효한 토큰을 위조할 수 있다.
그래서 코드에서는 실제 Secret은 환경 변수나 Secret 저장소로 주입하고 .env.example에는 예시만 두며,
운영에서는 Secret 커밋 여부, 환경 변수 출력 로그, 키 교체 이력을 확인해야 한다.
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
Secret Management 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.

