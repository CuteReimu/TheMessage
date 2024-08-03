package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.skill.getDrawCardCountEachTurn
import org.apache.logging.log4j.kotlin.logger

/**
 * 摸牌阶段
 */
data class DrawPhase(override val whoseTurn: Player) : Fsm {
    override fun resolve(): ResolveResult {
        if (!whoseTurn.alive) {
            return ResolveResult(NextTurn(whoseTurn), true)
        }
        whoseTurn.game!!.turn++
        whoseTurn.game!!.realTurn++
        logger.info("${whoseTurn}的回合开始了")
        for (p in whoseTurn.game!!.players) {
            p!!.notifyDrawPhase()
        }
        whoseTurn.draw(whoseTurn.getDrawCardCountEachTurn())
        return ResolveResult(MainPhaseIdle(whoseTurn), true)
    }

    override fun toString(): String {
        return "${whoseTurn}的摸牌阶段"
    }
}
