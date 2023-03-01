package com.fengsheng

import io.netty.util.HashedWheelTimer
import io.netty.util.Timeout
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import java.util.concurrent.TimeUnit

object GameExecutor {
    val TimeWheel = HashedWheelTimer()

    /**
     * （重要）由游戏的主线程去执行一段逻辑。
     *
     * 绝大部分逻辑代码都应该由游戏的主线程去执行，因此不需要加锁。
     */
    fun post(game: Game, callback: Runnable) {
        try {
            game.queue.offer(callback)
        } catch (_: ClosedReceiveChannelException) {
            // Ignored
        }
    }

    /**
     * （重要）在一段时间延迟后，由游戏的主线程去执行一段逻辑。
     *
     * 绝大部分逻辑代码都应该由游戏的主线程去执行，因此不需要加锁。
     */
    fun post(game: Game, callback: Runnable, delay: Long, unit: TimeUnit): Timeout {
        return TimeWheel.newTimeout({ post(game, callback) }, delay, unit)
    }
}