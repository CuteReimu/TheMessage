package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.skill.getDrawCardCountEachTurn
import org.apache.logging.log4j.kotlin.logger

/**
 * 摸牌阶段
 */
data class DrawPhase(val player: Player) : Fsm {
    override fun resolve(): ResolveResult {
        if (!player.alive) {
            return ResolveResult(NextTurn(player), true)
        }
        player.game!!.turn++
        logger.info("${player}的回合开始了")
        for (p in player.game!!.players) {
            p!!.notifyDrawPhase()
        }
        player.draw(player.getDrawCardCountEachTurn())
        return ResolveResult(MainPhaseIdle(player), true)
    }

    override fun toString(): String {
        return player.toString() + "的摸牌阶段"
    }
}
