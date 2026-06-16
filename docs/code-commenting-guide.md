# Code Commenting Guide

PayFlow 코드를 구현하거나 수정할 때는 주석을 반드시 함께 작성한다.

## 기본 원칙

- 핵심 비즈니스 규칙에는 반드시 주석을 단다.
- 단순히 코드가 하는 일을 반복하지 말고, 왜 그렇게 처리하는지 설명한다.
- 멱등성, 트랜잭션, 락, 보안 헤더, JWT, 이벤트 발행, 보상 처리처럼 장애나 돈의 정합성과 관련된 부분은 상세히 설명한다.
- 나중에 처음 보는 사람이 배운다는 마음으로 읽을 수 있게, 원리와 의도를 함께 적는다.
- 임시 구현이나 운영 전 보완이 필요한 부분은 `TODO:` 주석으로 남기고, 어떤 위험을 줄이기 위한 작업인지 적는다.

## 좋은 주석 예시

```java
// 같은 Idempotency-Key로 다른 금액/수신자 요청이 들어오면 기존 결과를 주면 안 된다.
// 클라이언트 버그나 키 재사용을 명확히 드러내기 위해 409 계열 에러로 처리한다.
if (!transfer.getRequestHash().equals(requestHash)) {
    throw new BusinessException(ErrorCode.IDEMPOTENCY_REQUEST_MISMATCH);
}
```

```java
// 잔액 변경은 반드시 row lock을 잡고 처리한다.
// 동시에 두 출금 요청이 들어왔을 때 둘 다 같은 초기 잔액을 보고 성공하는 lost update 문제를 막기 위해서다.
Wallet wallet = walletRepository.findByIdForUpdate(walletId)
        .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
```

## 피해야 할 주석

```java
// user를 저장한다.
userRepository.save(user);
```

위 주석은 코드와 같은 말만 반복하므로 도움이 적다. 대신 저장 시점에 unique 제약을 즉시 확인하려는 이유, 트랜잭션 경계, 예외 변환 의도를 적는다.

## 구현 체크리스트

- 새 서비스 메서드를 만들 때 흐름의 시작과 끝, 실패 처리 이유를 주석으로 남겼는가?
- 금액 변경 로직에 정합성, 락, 멱등성 주석을 남겼는가?
- 외부/내부 API 경계에 인증과 신뢰할 수 있는 값의 기준을 설명했는가?
- Kafka 이벤트나 비동기 처리는 중복 전달 가능성과 재처리 기준을 설명했는가?
- 테스트에서 중요한 시나리오는 무엇을 검증하는지 주석 또는 테스트명으로 드러나는가?
