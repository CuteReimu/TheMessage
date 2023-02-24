package com.fengsheng.phase

import com.fengsheng.Config
import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import org.apache.log4j.Logger

/**
 * 摸牌阶段
 */
data class DrawPhase(val player: Player) : Fsm {
    override fun resolve(): ResolveResult {
        if (!player.alive) {
            return ResolveResult(NextTurn(player), true)
        }
        log.info("${player}的回合开始了")
        for (p in player.game!!.players) {
            p!!.notifyDrawPhase()
        }
        player.draw(Config.HandCardCountEachTurn)
        return ResolveResult(MainPhaseIdle(player), true)
    }

    override fun toString(): String {
        return player.toString() + "的摸牌阶段"
    }

    companion object {
        private val log = Logger.getLogger(DrawPhase::class.java)
    }
}