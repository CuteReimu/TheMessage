package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.ReceivePhaseSenderSkillimport

com.fengsheng.protos.Common.color com.fengsheng.protos.Role
import java.util.concurrent.LinkedBlockingQueue
import io.netty.util.HashedWheelTimerimport

org.apache.log4j.Logger
/**
 * 老鳖技能【明饵】：你传出的红色或蓝色情报被接收后，你和接收者各摸一张牌。
 */
class MingEr : AbstractSkill(), TriggeredSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.MING_ER
    }

    override fun execute(g: Game): ResolveResult? {
        if (g.fsm !is ReceivePhaseSenderSkill || fsm.whoseTurn.findSkill<Skill>(skillId) == null || !fsm.whoseTurn.isAlive()) return null
        if (fsm.whoseTurn.getSkillUseCount(skillId) > 0) return null
        val colors: List<color> = fsm.messageCard.getColors()
        if (!colors.contains(color.Red) && !colors.contains(color.Blue)) return null
        fsm.whoseTurn.addSkillUseCount(skillId)
        log.info(fsm.whoseTurn.toString() + "发动了[明饵]")
        for (p in g.players) {
            (p as? HumanPlayer)?.send(
                Role.skill_ming_er_toc.newBuilder().setPlayerId(p.getAlternativeLocation(fsm.whoseTurn.location()))
                    .build()
            )
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