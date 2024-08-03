package com.fengsheng.phase

import com.fengsheng.*
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 游戏马上开始
 */
data class StartGame(val game: Game, override val whoseTurn: Player) : Fsm {
    override fun resolve(): ResolveResult? {
        val whoseTurn = whoseTurn.location
        val players = game.players
        logger.info("游戏开始了，场上的角色依次是：${players.joinToString()}")
        game.turn = 0
        game.realTurn = 0
        game.deck.init(players.size)
        for (i in players.indices) players[(whoseTurn + i) % players.size]!!.init()
        for (i in players.indices) players[(whoseTurn + i) % players.size]!!.draw(Config.HandCardCountBegin)
        GameExecutor.post(game, { game.resolve(DrawPhase(players[whoseTurn]!!)) }, 1, TimeUnit.SECONDS)
        return null
    }
}
