package com.fengsheng

import io.netty.util.HashedWheelTimer
import io.netty.util.Timeout
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.TimeUnit

object GameExecutor {
    val TimeWheel = HashedWheelTimer()

    /**
     * 由游戏的协程去执行一段逻辑，并阻塞等待返回。
     */
    suspend fun <T : Any> call(game: Game, callback: suspend () -> T): T {
        val channel = Channel<T>(1)
        game.queue.send { channel.send(callback()) }
        return channel.receive()
    }

    /**
     * （重要）由游戏的协程去执行一段逻辑。
     *
     * 绝大部分逻辑代码都应该由游戏的协程去执行，因此不需要加锁。
     */
    fun post(game: Game, callback: suspend () -> Unit) {
        game.queue.trySend(callback)
    }

    /**
     * （重要）在一段时间延迟后，由游戏的协程去执行一段逻辑。
     *
     * 绝大部分逻辑代码都应该由游戏的协程去执行，因此不需要加锁。
     */
    fun post(game: Game, callback: suspend () -> Unit, delay: Long, unit: TimeUnit): Timeout {
        return TimeWheel.newTimeout({ post(game, callback) }, delay, unit)
    }
}