package com.fengsheng.phase

import com.fengsheng.*
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 游戏马上开始
 */
data class StartGame(val game: Game, val whoseTurn: Int) : Fsm {
    override fun resolve(): ResolveResult? {
        val players = game.players
        logger.info("游戏开始了，场上的角色依次是：${players.joinToString()}")
        game.deck.init(players.size)
        for (i in players.indices) players[(whoseTurn + i) % players.size]!!.init()
        for (i in players.indices) {
            val player = players[(whoseTurn + i) % players.size]!!
            if (player is HumanPlayer && players.size in 5..8) {
                val score = Statistics.getScore(player.playerName)!!
                val addShiTanCount = when {
                    score >= 40 -> 0
                    score >= 20 -> Random.nextInt(2)
                    else -> when (Random.nextInt(4)) {
                        3 -> 2
                        1, 2 -> 1
                        else -> 0
                    }
                }
                game.deck.pushShiTan(addShiTanCount)
            }
            player.draw(Config.HandCardCountBegin)
        }
        GameExecutor.post(game, { game.resolve(DrawPhase(players[whoseTurn]!!)) }, 1, TimeUnit.SECONDS)
        return null
    }
}