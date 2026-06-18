package com.payflow.transfer.lock;

import java.time.Duration;

public interface DistributedLock {

    boolean tryLock(String key, String ownerToken, Duration ttl);

    void unlock(String key, String ownerToken);
}
