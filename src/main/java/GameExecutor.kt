package com.fengsheng

import io.netty.util.HashedWheelTimer
import io.netty.util.Timeout
import org.apache.log4j.Logger
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class GameExecutor private constructor() : Runnable {
    private val queue: BlockingQueue<GameAndCallback> = LinkedBlockingQueue()
    override fun run() {
        while (true) try {
            val gameAndCallback = queue.take()
            if (!gameAndCallback.game.isEnd) gameAndCallback.callback.run()
        } catch (e: InterruptedException) {
            log.error("take queue interrupted", e)
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            log.error("catch throwable", e)
        }
    }

    private fun post(gameAndCallback: GameAndCallback) {
        try {
            queue.put(gameAndCallback)
        } catch (e: InterruptedException) {
            log.error("put queue interrupted", e)
            Thread.currentThread().interrupt()
        }
    }

    private data class GameAndCallback(val game: Game, val callback: Runnable)

    companion object {
        private val log = Logger.getLogger(GameExecutor::class.java)
        private val executors = arrayOfNulls<GameExecutor>((Runtime.getRuntime().availableProcessors() + 1) / 2)
        val TimeWheel = HashedWheelTimer()

        /**
         * （重要）由游戏的主线程去执行一段逻辑。
         *
         *
         * 绝大部分逻辑代码都应该由游戏的主线程去执行，因此不需要加锁。
         */
        fun post(game: Game, callback: Runnable) {
            val mod: Int = game.id % executors.size
            if (executors[mod] == null) {
                synchronized(GameExecutor::class.java) {
                    if (executors[mod] == null) {
                        executors[mod] = GameExecutor()
                        val thread = Thread(executors[mod])
                        thread.isDaemon = true
                        thread.start()
                    }
                }
            }
            executors[mod]!!.post(GameAndCallback(game, callback))
        }

        /**
         * （重要）在一段时间延迟后，由游戏的主线程去执行一段逻辑。
         *
         *
         * 绝大部分逻辑代码都应该由游戏的主线程去执行，因此不需要加锁。
         */
        fun post(game: Game, callback: Runnable, delay: Long, unit: TimeUnit): Timeout {
            return TimeWheel.newTimeout({ post(game, callback) }, delay, unit)
        }
    }
}