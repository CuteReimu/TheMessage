package com.fengsheng

import java.util.concurrent.TimeUnit

/**
 * 令牌桶，不是线程安全的
 */
class Limiter(private val limit: Long, recoverDuration: Long, unit: TimeUnit) {
    private var left = limit
    private var lastRecoverNanoTime = System.nanoTime()
    private val recoverDuration = unit.toNanos(recoverDuration)

    fun allow(n: Long = 1): Boolean {
        var newLeft = left
        if (left < limit) {
            val recover = (System.nanoTime() - lastRecoverNanoTime) / recoverDuration
            newLeft = limit.coerceAtMost(left + recover)
            lastRecoverNanoTime =
                if (newLeft == limit) lastRecoverNanoTime else lastRecoverNanoTime + recoverDuration * recover
        }
        newLeft -= n
        if (newLeft < 0) return false
        left = newLeft
        return true
    }
}