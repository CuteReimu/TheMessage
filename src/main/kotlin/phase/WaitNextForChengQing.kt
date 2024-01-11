package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.ResolveResult
import com.fengsheng.skill.cannotPlayCardAndSkillForChengQing
import org.apache.log4j.Logger

/**
 * 濒死求澄清时，询问下一个人
 */
data class WaitNextForChengQing(val waitForChengQing: WaitForChengQing) : Fsm {
    override fun resolve(): ResolveResult {
        val game = waitForChengQing.askWhom.game!!
        val players = game.players
        var askWhom = waitForChengQing.askWhom.location
        while (true) {
            askWhom = (askWhom + 1) % players.size
            if (askWhom == waitForChengQing.whoDie.location) {
                log.info("无人拯救，${waitForChengQing.whoDie}已死亡")
                waitForChengQing.diedQueue.add(waitForChengQing.whoDie)
                return ResolveResult(
                    StartWaitForChengQing(
                        waitForChengQing.whoseTurn,
                        waitForChengQing.dyingQueue,
                        waitForChengQing.diedQueue,
                        waitForChengQing.afterDieResolve
                    ), true
                )
            }
            if (players[askWhom]!!.alive && !players[askWhom]!!.cannotPlayCardAndSkillForChengQing()) {
                return ResolveResult(waitForChengQing.copy(askWhom = players[askWhom]!!), true)
            }
        }
    }

    companion object {
        private val log = Logger.getLogger(WaitNextForChengQing::class.java)
    }
}