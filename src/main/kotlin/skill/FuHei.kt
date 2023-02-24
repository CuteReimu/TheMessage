package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.ReceivePhaseSenderSkillimport

com.fengsheng.protos.Common.color com.fengsheng.protos.Role
import java.util.concurrent.LinkedBlockingQueue
import io.netty.util.HashedWheelTimerimport

org.apache.log4j.Logger
/**
 * 白菲菲技能【腹黑】：你传出的黑色情报被接收后，你摸一张牌。
 */
class FuHei : AbstractSkill(), TriggeredSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.FU_HEI
    }

    override fun execute(g: Game): ResolveResult? {
        if (g.fsm !is ReceivePhaseSenderSkill || fsm.whoseTurn.findSkill<Skill>(skillId) == null || !fsm.whoseTurn.isAlive()) return null
        if (fsm.whoseTurn.getSkillUseCount(skillId) > 0) return null
        val colors: List<color> = fsm.messageCard.getColors()
        if (!colors.contains(color.Black)) return null
        fsm.whoseTurn.addSkillUseCount(skillId)
        log.info(fsm.whoseTurn.toString() + "发动了[腹黑]")
        for (p in g.players) {
            (p as? HumanPlayer)?.send(
                Role.skill_fu_hei_toc.newBuilder().setPlayerId(p.getAlternativeLocation(fsm.whoseTurn.location()))
                    .build()
            )
        }
        fsm.whoseTurn.draw(1)
        return null
    }

    companion object {
        private val log = Logger.getLogger(FuHei::class.java)
    }
}