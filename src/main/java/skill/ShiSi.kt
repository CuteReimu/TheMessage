package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.ReceivePhaseReceiverSkillimport

com.fengsheng.protos.Common.color com.fengsheng.protos.Role
import java.util.concurrent.LinkedBlockingQueue
import io.netty.util.HashedWheelTimerimport

org.apache.log4j.Logger
/**
 * 老汉技能【视死】：你接收黑色情报后，摸两张牌。
 */
class ShiSi : AbstractSkill(), TriggeredSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.SHI_SI
    }

    override fun execute(g: Game): ResolveResult? {
        if (g.fsm !is ReceivePhaseReceiverSkill || fsm.inFrontOfWhom.findSkill<Skill>(skillId) == null || !fsm.inFrontOfWhom.isAlive()) return null
        if (fsm.inFrontOfWhom.getSkillUseCount(skillId) > 0) return null
        val colors: List<color> = fsm.messageCard.getColors()
        if (!colors.contains(color.Black)) return null
        fsm.inFrontOfWhom.addSkillUseCount(skillId)
        log.info(fsm.inFrontOfWhom.toString() + "发动了[视死]")
        for (p in g.players) {
            (p as? HumanPlayer)?.send(
                Role.skill_shi_si_toc.newBuilder().setPlayerId(p.getAlternativeLocation(fsm.inFrontOfWhom.location()))
                    .build()
            )
        }
        fsm.inFrontOfWhom.draw(2)
        return null
    }

    companion object {
        private val log = Logger.getLogger(ShiSi::class.java)
    }
}