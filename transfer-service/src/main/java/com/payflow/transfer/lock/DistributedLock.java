package com.payflow.transfer.lock;

import java.time.Duration;

public interface DistributedLock {

    boolean tryLock(
            String key,
            String ownerToken,
            Duration ttl,
            Duration waitTimeout,
            Duration retryInterval
    );

    void unlock(String key, String ownerToken);
}
