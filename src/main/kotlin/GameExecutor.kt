package com.fengsheng

import akka.actor.*
import akka.japi.pf.DeciderBuilder
import akka.pattern.Patterns
import io.netty.util.HashedWheelTimer
import io.netty.util.Timeout
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class StopGameActor

class GameActor : AbstractActor() {
    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(Runnable::class.java) {
                it.run()
            }
            .match(Callable::class.java) {
                sender.tell(it.call(), self)
            }
            .match(StopGameActor::class.java) {
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
        return TimeWheel.newTimeout({ post(game, callback) }, delay, unit)
    }

    fun newGame(lastTotalPlayerCount: Int): Game {
        val id = Game.increaseId.incrementAndGet()
        val actorRef = system.actorOf(Props.create(GameActor::class.java), "game-$id")
        return Game(id, lastTotalPlayerCount, actorRef)
    }
}