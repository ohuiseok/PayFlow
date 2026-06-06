# 08. Redis Lock

이 문서는 Redis 분산 락 구현 계획이다.

## 목적

같은 지갑에서 동시에 여러 송금 요청이 들어와도 잔액이 중복 차감되지 않도록 한다.

## 적용 위치

보강/2차 구현에서는 `transfer-service`에서 지갑 단위 락을 잡는다.

이유:

```text
송금은 senderWalletId와 receiverWalletId 두 지갑을 함께 다룬다.
transfer-service가 전체 흐름을 알고 있다.
wallet-service는 DB row lock으로 마지막 방어선을 둔다.
```

## 락 키

단일 지갑 락:

```text
lock:wallet:{walletId}
```

송금 시 두 지갑 락:

```text
lock:wallet:{senderWalletId}
lock:wallet:{receiverWalletId}
```

## 데드락 방지

두 지갑을 잠글 때는 항상 작은 walletId부터 잠근다.

예:

```text
senderWalletId = 10
receiverWalletId = 3

lock wallet 3
lock wallet 10
```

## 락 획득 방식

Redis 명령:

```text
SET key value NX PX ttl
```

권장:

```text
ttl: 5초
wait time: 2초
retry interval: 100ms
```

## 락 value

락 value는 UUID로 둔다.

해제 시 value를 비교하고 자기 락만 삭제한다.

Lua script 사용:

```lua
if redis.call("get", KEYS[1]) == ARGV[1] then
  return redis.call("del", KEYS[1])
else
  return 0
end
```

## 구현 클래스

위치:

```text
transfer-service/src/main/java/com/payflow/transfer/infrastructure/redis
```

클래스:

```text
RedisLockManager
RedisLock
LockAcquireException
```

사용 예:

```java
try (RedisLock lock = lockManager.acquire(walletIds)) {
    transferService.process(command);
}
```

## 실패 정책

락 획득 실패:

```text
HTTP 409 Conflict
ErrorCode: WALLET_LOCK_CONFLICT
Message: 잠시 후 다시 시도해주세요.
```

락 처리 중 예외:

```text
반드시 finally 또는 AutoCloseable로 락 해제
```

## 테스트

보강/2차 필수 테스트:

```text
단일 락 획득 성공
중복 락 획득 실패
락 value가 다르면 삭제 실패
두 지갑 락 정렬 확인
동시 송금 요청에서 한 번씩 순차 처리
```
