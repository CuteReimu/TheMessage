package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.ResolveResult
import com.fengsheng.phase.ReceivePhaseSenderSkill
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Role.skill_fu_hei_toc
import org.apache.log4j.Logger

/**
 * 白菲菲技能【腹黑】：你传出的黑色情报被接收后，你摸一张牌。
 */
class FuHei : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.FU_HEI

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm as? ReceivePhaseSenderSkill
        if (fsm == null || fsm.whoseTurn.findSkill(skillId) == null || !fsm.whoseTurn.alive) return null
        if (fsm.whoseTurn.getSkillUseCount(skillId) > 0) return null
        val colors = fsm.messageCard.colors
        if (!colors.contains(color.Black)) return null
        fsm.whoseTurn.addSkillUseCount(skillId)
        log.info("${fsm.whoseTurn}发动了[腹黑]")
        for (p in g.players) {
            (p as? HumanPlayer)?.send(
                skill_fu_hei_toc.newBuilder().setPlayerId(p.getAlternativeLocation(fsm.whoseTurn.location)).build()
            )
        }
        fsm.whoseTurn.draw(1)
        return null
    }

    companion object {
        private val log = Logger.getLogger(FuHei::class.java)
    }
}