# JVM Memory Structure

## 핵심 개념

JVM 메모리는 Java 애플리케이션이 실행될 때 사용하는 메모리 영역이다. 대표적으로 Heap, Stack, Metaspace, Code Cache가 있다.

Heap에는 객체가 저장되고, Stack에는 메서드 호출 정보와 지역 변수가 저장된다. Metaspace에는 클래스 메타데이터가 저장된다.

### JVM 메모리 영역 상세 구조

JVM 프로세스가 사용하는 전체 메모리는 다음 영역으로 구성된다.

```text
JVM 프로세스 메모리
├── Heap (GC가 관리하는 영역)
│   ├── Young Generation
│   │   ├── Eden Space
│   │   └── Survivor Spaces (S0, S1)
│   └── Old Generation (Tenured)
│
├── Non-Heap
│   ├── Metaspace (클래스 메타데이터)
│   ├── Code Cache (JIT 컴파일된 네이티브 코드)
│   └── Compressed Class Space
│
├── Thread Stack (스레드마다 별도 스택)
├── Direct Buffer (NIO ByteBuffer.allocateDirect)
└── Native Memory (JVM 내부 구조체)
```

컨테이너 메모리 제한을 설정할 때 Heap만 고려하면 안 된다. 전체 JVM 프로세스는 Heap 외에도 Metaspace, Code Cache, Thread Stack, Native Memory를 사용한다. 일반적으로 컨테이너 메모리의 70~80%를 Heap으로 할당하고 나머지는 여유를 둔다.

### Heap 영역의 세대별 구조

JVM Heap은 객체의 생존 기간에 따라 세대(Generation)로 나뉜다.

```text
Young Generation:
  - 새로 생성된 객체가 할당되는 영역
  - Eden: 대부분의 객체가 처음 할당되는 곳
  - Survivor S0, S1: Minor GC 후 살아남은 객체가 이동
  - 크기가 작아 GC가 빠름 (Minor GC)
  - 대부분의 객체는 여기서 죽음 (Short-lived)

Old Generation:
  - Young Generation에서 오래 살아남은 객체가 이동 (Promotion)
  - 크기가 큼, GC 비용도 큼 (Major GC, Full GC)
  - Spring Bean, 캐시 데이터, 오래된 HTTP 세션 등
```

세대 가설(Generational Hypothesis): 대부분의 객체는 생성 직후 죽는다. DTO, 요청 파싱 객체, 임시 계산 결과는 대부분 요청 처리 후 버려진다. 이 특성을 활용해 Young Generation GC를 빠르게 처리하도록 설계되어 있다.

### Stack 메모리

각 스레드는 자신만의 Stack을 가진다.

```text
스레드 스택 구조:
  스레드 1 스택
    └── Frame: main()
          └── Frame: handleRequest()
                └── Frame: processTransfer()
                      └── Frame: validateWallet()
```

각 메서드 호출은 Stack Frame을 생성한다. Frame에는 다음이 저장된다.

```text
- 지역 변수 (primitive 타입 값, 객체 참조)
- 연산 스택 (중간 계산 결과)
- 메서드 반환 주소
```

Stack은 스레드당 기본 256KB~1MB다. 재귀 호출이 너무 깊어지면 StackOverflowError가 발생한다. Spring에서 스레드가 200개면 Stack만으로 200MB ~ 1GB를 사용할 수 있다.

### Metaspace

Java 8부터 PermGen이 없어지고 Metaspace가 도입됐다.

```text
Metaspace 저장 내용:
  - 클래스 메타데이터 (메서드 정보, 필드 정보)
  - static 변수
  - 인터페이스 정보

PermGen과의 차이:
  - PermGen: JVM Heap 안의 고정 크기 공간 -> OutOfMemoryError: PermGen space
  - Metaspace: Native Memory 사용, 기본적으로 상한 없음
  - Metaspace는 자동으로 확장 가능하지만 제한을 두지 않으면 OS 메모리를 모두 쓸 수 있음
```

Spring Boot 애플리케이션은 클래스 로딩이 많아 Metaspace 사용량이 크다. 동적 클래스 로딩(AOP 프록시, Reflection)이 많은 경우 Metaspace가 지속 증가할 수 있다.

```bash
# Metaspace 제한 설정 예시
-XX:MaxMetaspaceSize=256m
```

### JVM 메모리 설정 방법

```bash
# 기본 Heap 설정
-Xms512m        # 초기 Heap 크기
-Xmx1024m       # 최대 Heap 크기

# 컨테이너 환경 (Java 8 update 191+, Java 11+)
-XX:+UseContainerSupport          # 컨테이너 메모리 제한 인식
-XX:MaxRAMPercentage=75.0         # 컨테이너 메모리의 75%를 Heap으로

# Spring Boot에서 환경 변수로 설정
JAVA_OPTS=-Xms256m -Xmx512m
```

컨테이너 환경에서는 `-XX:+UseContainerSupport`를 반드시 추가해야 한다. 이 옵션 없이 컨테이너 메모리 제한을 설정하면, JVM이 컨테이너 제한이 아닌 호스트의 전체 메모리를 기준으로 Heap을 설정한다.

### 메모리 문제 진단

**OutOfMemoryError 종류별 원인**

```text
OutOfMemoryError: Java heap space
  - 원인: 객체가 너무 많이 생성되어 Heap이 가득 참
  - 대량 조회, 메모리 누수, Heap 크기 부족

OutOfMemoryError: Metaspace
  - 원인: 너무 많은 클래스가 로딩됨
  - 동적 클래스 생성 과다, MaxMetaspaceSize 미설정

OutOfMemoryError: Direct buffer memory
  - 원인: NIO Direct Buffer 과다 할당
  - Netty, Kafka 클라이언트 등의 native buffer

StackOverflowError
  - 원인: 재귀 호출이 너무 깊어짐
  - 순환 참조 직렬화, 무한 재귀
```

**메모리 누수 패턴**

```text
결제 시스템에서 자주 발생하는 메모리 누수:
  - static 컬렉션에 계속 추가만 하는 경우
  - 캐시에 만료 설정 없이 계속 추가
  - ThreadLocal에 값을 설정하고 remove 안 함
  - JPA EntityManager를 close 하지 않음
  - 이벤트 리스너 등록 후 해제 안 함
```

### 흔한 오해와 함정

Heap을 크게 잡으면 좋다는 생각은 잘못됐다. Heap이 크면 GC가 더 오래 걸린다. 특히 Full GC 시 Heap 전체를 스캔하므로 Heap이 클수록 Stop-The-World가 길어진다.

`-Xms`와 `-Xmx`를 같게 설정하는 것은 장단점이 있다. 같게 설정하면 Heap 크기 조정 오버헤드가 없고 예측 가능하지만, 처음부터 전체 메모리를 예약하므로 다른 프로세스에 여유가 없다. 컨테이너 환경에서는 `-XX:MaxRAMPercentage`를 쓰는 방식이 더 유연하다.

## PayFlow 연결

PayFlow의 각 서비스는 Spring Boot 애플리케이션으로 실행된다. Docker Compose 환경에서 여러 서비스를 동시에 띄우므로, 서비스별 JVM 메모리 제한이 중요하다.

특히 작은 EC2 인스턴스에서 Gateway, user, wallet, transfer, ledger, Kafka, MySQL, Redis를 함께 띄우면 메모리 부족이 쉽게 발생할 수 있다.

## 실무 포인트

- 컨테이너 메모리 제한과 JVM Heap 설정을 함께 본다.
- OutOfMemoryError는 단순히 힙만의 문제가 아닐 수 있다.
- 대량 조회는 객체를 많이 만들어 GC 부담을 늘린다.
- 로그, JSON 직렬화, JPA 영속성 컨텍스트도 메모리를 사용한다.

## 체크 질문

- Heap과 Stack의 차이는 무엇인가
- Spring Boot 서비스를 여러 개 띄울 때 JVM 메모리 설정이 중요한 이유는 무엇인가
- 대량 데이터를 한 번에 조회하면 어떤 문제가 생길 수 있는가

## 실무 설계 참고

### 대표 장애 시나리오

EC2 한 대에서 여러 Spring Boot 서비스와 Kafka/MySQL을 띄우다가 메모리 부족으로 서비스가 재시작된다.

### 잘못된 구현 예시

~~~text
JVM heap과 컨테이너 메모리 제한을 따로 생각한다.
~~~

### 좋은 구현 예시

~~~text
서비스별 heap, container limit, 대량 조회 제한, GC 지표를 함께 관리한다.
~~~

### 대안과 선택 이유

서버 자원을 크게 잡아 문제를 덮는 방식도 있지만, 작은 EC2와 여러 MSA 서비스를 함께 쓰는 PayFlow에서는 비용과 한계가 금방 드러난다. 메모리, 스레드, 커넥션 풀을 측정 가능한 자원으로 보고 제한과 타임아웃을 두는 방식이 더 안정적이다.

### PayFlow에서 확인할 위치

docker-compose.yml memory 설정, 각 service Dockerfile, JVM option, 운영 로그

### 면접에서 설명하기

JVM 메모리는 코드 내부 문제가 아니라 배포 구조와도 연결된다. MSA에서는 서비스 수만큼 JVM도 늘어난다.

### 관련 문서

14, 15, 16, 78

## 질문의 질문으로 더 깊게 보기

### 이 문서에서 잡아야 할 CS 감각

이 구간의 핵심은 서버 애플리케이션이 무한한 자원 위에서 동작하지 않는다는 사실이다. JVM 메모리, 스레드, DB 커넥션은 모두 제한된 자원이고, 하나가 막히면 나머지도 연쇄적으로 느려질 수 있다.

### 질문의 질문

아래 질문들은 답을 외우기 위한 질문이 아니다. 하나씩 답하면서 "왜 그런 설계가 필요한지"를 설명하는 연습을 하기 위한 질문이다.

- 이 병목은 CPU, 메모리, 스레드, 커넥션 중 어디에서 시작되는가?
- 자원을 늘리면 해결되는 문제인가, 대기 시간을 줄여야 하는 문제인가?
- 장애 상황에서 제한된 자원을 어떤 요청이 먼저 차지하게 되는가?
- 이 개념을 적용하면 어떤 문제가 해결되지만, 어떤 비용이나 복잡도가 새로 생기는가?
- 장애가 발생했을 때 이 개념은 문제를 예방하는가, 감지하는가, 복구하는가?
- PayFlow 코드에서 이 개념을 확인하려면 어떤 서비스, 테이블, 로그, 테스트를 봐야 하는가?

### 답안 예시

<details>
<summary>답안 예시 펼치기</summary>

#### 핵심 답변

JVM Memory Structure 개념은 PayFlow에서 다음 이유로 중요하다.

- JVM과 자원 관리 개념은 PayFlow 서비스가 제한된 메모리, 스레드, 커넥션 안에서 안정적으로 요청을 처리하기 위해 필요하다.
- 각 Spring Boot 서비스의 JVM, 웹 스레드 풀, HikariCP, DB가 자원 상태의 기준이다.
- 외부 호출 지연이나 DB 대기가 길어지면 스레드와 커넥션이 고갈되고 정상 요청까지 느려진다.
- 타임아웃, 풀 크기 조정, 트랜잭션 단축, 대량 조회 제한, GC와 커넥션 지표 모니터링으로 방어한다.
- 운영에서는 Heap 사용량, GC pause, active thread, Hikari active/idle/pending, DB connection 수를 본다.

#### 질문에 답하는 방식

좋은 답변은 용어 정의에서 멈추지 않고 다음 순서로 이어져야 한다.

1. 어떤 데이터나 요청을 보호하려는 개념인지 말한다.
2. 그 데이터의 진실의 원천이 어느 서비스에 있는지 말한다.
3. 장애, 중복, 동시성, 지연 중 어떤 상황에서 문제가 생기는지 설명한다.
4. 코드 레벨의 방어 수단과 운영 레벨의 확인 수단을 함께 말한다.

#### PayFlow 예시 답변

```text
JVM Memory Structure 개념은 PayFlow에서 여러 Spring Boot 서비스가 제한된 메모리 안에서 안정적으로 실행되게 하기 위해 필요하다.
이 개념이 없으면 wallet-service나 transfer-service가 OOM으로 죽어 송금 중간 상태가 남을 수 있다.
그래서 코드에서는 컨테이너 메모리 제한과 JVM heap 설정을 맞추고 대량 조회를 제한하며,
운영에서는 Heap 사용량, OOM 로그, 컨테이너 재시작 횟수를 확인해야 한다.
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
JVM Memory Structure 개념은 PayFlow에서 ______ 문제를 막기 위해 필요하다.
이 개념이 없으면 ______ 상황에서 ______ 장애가 발생할 수 있다.
그래서 코드에서는 ______ 방식으로 방어하고,
운영에서는 ______ 지표나 로그로 확인해야 한다.
```

"정의"를 말할 수 있으면 1단계이고, "왜 필요한지"를 말할 수 있으면 2단계다. 실제 장애 상황에 대입해서 "어떻게 막고 어떻게 복구할지"까지 말할 수 있으면 그때부터 실무 지식이 된다.
