package com.fengsheng;

import java.util.concurrent.TimeUnit;

/**
 * 令牌桶，不是线程安全的
 */
public class Limiter {
    private final long limit;
    private long left;
    private long lastRecoverNanoTime;
    private final long recoverDuration;

    public Limiter(long limit, long recoverDuration, TimeUnit unit) {
        this.limit = limit;
        this.left = limit;
        this.lastRecoverNanoTime = System.nanoTime();
        this.recoverDuration = unit.toNanos(recoverDuration);
    }

    public boolean allow() {
        return allow(1);
    }

    public boolean allow(long n) {
        long newLeft = left;
        if (left < limit) {
            long recover = (System.nanoTime() - lastRecoverNanoTime) / recoverDuration;
            newLeft = Math.min(limit, left + recover);
            lastRecoverNanoTime = newLeft == limit ? lastRecoverNanoTime : lastRecoverNanoTime + recoverDuration * recover;
        }
        newLeft -= n;
        if (newLeft < 0)
            return false;
        left = newLeft;
        return true;
    }
}
