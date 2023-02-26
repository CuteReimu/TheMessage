package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.ResolveResult
import com.fengsheng.phase.ReceivePhaseSenderSkill
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Role.skill_ming_er_toc
import org.apache.log4j.Logger

/**
 * 老鳖技能【明饵】：你传出的红色或蓝色情报被接收后，你和接收者各摸一张牌。
 */
class MingEr : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.MING_ER

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm as? ReceivePhaseSenderSkill
        if (fsm == null || fsm.whoseTurn.findSkill(skillId) == null || !fsm.whoseTurn.alive) return null
        if (fsm.whoseTurn.getSkillUseCount(skillId) > 0) return null
        val colors: List<color> = fsm.messageCard.colors
        if (!colors.contains(color.Red) && !colors.contains(color.Blue)) return null
        fsm.whoseTurn.addSkillUseCount(skillId)
        log.info("${fsm.whoseTurn}发动了[明饵]")
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_ming_er_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(fsm.whoseTurn.location)
                p.send(builder.build())
            }
        }
        if (fsm.whoseTurn === fsm.inFrontOfWhom) {
            fsm.whoseTurn.draw(2)
        } else {
            fsm.whoseTurn.draw(1)
            fsm.inFrontOfWhom.draw(1)
        }
        return null
    }

    companion object {
        private val log = Logger.getLogger(MingEr::class.java)
    }
}