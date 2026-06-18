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
    public boolean tryLock(String key, String ownerToken, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, ownerToken, ttl);
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void unlock(String key, String ownerToken) {
        redisTemplate.execute(RELEASE_SCRIPT, List.of(key), ownerToken);
    }
}
