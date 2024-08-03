package com.fengsheng

import akka.actor.*
import akka.japi.pf.DeciderBuilder
import akka.pattern.Patterns
import io.netty.util.HashedWheelTimer
import io.netty.util.Timeout
import org.apache.logging.log4j.kotlin.logger
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class StopGameActor(val game: Game)

class GameActor : AbstractActor() {
    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(Runnable::class.java) {
                try {
                    it.run()
                } catch (e: Throwable) {
                    logger.error("Game Actor异常", e)
                }
            }
            .match(Callable::class.java) {
                try {
                    sender.tell(it.call(), self)
                } catch (e: Throwable) {
                    logger.error("Game Actor异常", e)
                }
            }
            .match(StopGameActor::class.java) {
                logger.info("房间销毁，rid=${it.game.id}")
                Game.gameCache.remove(it.game.id, it.game)
                it.game.players.forEach { p -> p?.reset() }
                it.game.players = emptyList()
                context.stop(self)
            }
            .build()
    }

    override fun supervisorStrategy(): SupervisorStrategy {
        return OneForOneStrategy(
            3,
            Duration.ofSeconds(5),
            DeciderBuilder.match(Throwable::class.java) { SupervisorStrategy.escalate() }.build()
        )
    }
}

object GameExecutor {
    val TimeWheel = HashedWheelTimer()
    private val system = ActorSystem.create("fengsheng")

    /**
     * 由游戏的协程去执行一段逻辑，并阻塞等待返回。
     */
    fun <T : Any> call(game: Game, callback: () -> T): T {
        val future = Patterns.ask(game.actorRef, Callable {
            return@Callable callback()
        }, Duration.ofSeconds(5)) as CompletableFuture<*>
        @Suppress("UNCHECKED_CAST")
        return future.get() as T
    }

    /**
     * （重要）由游戏的协程去执行一段逻辑。
     *
     * 绝大部分逻辑代码都应该由游戏的协程去执行，因此不需要加锁。
     */
    fun post(game: Game, callback: () -> Unit) {
        game.actorRef.tell(Runnable { callback() }, ActorRef.noSender())
    }

    /**
     * （重要）在一段时间延迟后，由游戏的协程去执行一段逻辑。
     *
     * 绝大部分逻辑代码都应该由游戏的协程去执行，因此不需要加锁。
     */
    fun post(game: Game, callback: () -> Unit, delay: Long, unit: TimeUnit): Timeout {
        if (delay == 3L && unit == TimeUnit.SECONDS) {
            if (!game.players.any { it is HumanPlayer && it.alive })
                return TimeWheel.newTimeout({ post(game, callback) }, 1, unit)
            if (Config.IsGmEnable) return TimeWheel.newTimeout({ post(game, callback) }, 2, unit)
            val minScore = game.players.minOf { if (it is HumanPlayer) Statistics.getScore(it.playerName) ?: 0 else 9999 }
            if (minScore < 70)
                return TimeWheel.newTimeout({ post(game, callback) }, (10 - minScore / 10).toLong(), unit)
        }
        return TimeWheel.newTimeout({ post(game, callback) }, delay, unit)
    }

    fun getGame(id: Int, playerCount: Int): Game {
        val count =
            if (playerCount == 0) Config.TotalPlayerCount
            else if (Config.IsGmEnable) playerCount.coerceIn(2..9)
            else playerCount.coerceIn(5..9)
        // 找房间
        if (id == 0) {
            val game = Game.gameCache.values.shuffled().find { !it.isStarted }
            if (game != null) return game
        } else {
            val game = Game.gameCache[id]
            if (game != null) return game
        }
        // 新建房间
        val newId = if (id == 0) {
            var id0: Int
            while (true) {
                id0 = Game.increaseId.incrementAndGet()
                if (!Game.gameCache.containsKey(id0)) break
            }
            id0
        } else {
            id
        }
        val actorRef = system.actorOf(Props.create(GameActor::class.java), "game-$newId")
        val game = Game(newId, count, actorRef)
        if (Game.gameCache.putIfAbsent(newId, game) != null) {
            game.actorRef.tell(StopGameActor(game), ActorRef.noSender())
            throw IllegalStateException("游戏ID冲突")
        }
        return game
    }
}
