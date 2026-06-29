package com.payflow.transfer.lock;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisDistributedLock implements DistributedLock {

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean tryLock(
            String key,
            String ownerToken,
            Duration ttl,
            Duration waitTimeout,
            Duration retryInterval
    ) {
        long deadline = System.nanoTime() + waitTimeout.toNanos();
        do {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, ownerToken, ttl);
            if (Boolean.TRUE.equals(acquired)) {
                return true;
            }
            if (System.nanoTime() >= deadline) {
                return false;
            }
            try {
                Thread.sleep(Math.max(1L, retryInterval.toMillis()));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        } while (true);
    }

    @Override
    public void unlock(String key, String ownerToken) {
        redisTemplate.execute(RELEASE_SCRIPT, List.of(key), ownerToken);
    }
}
