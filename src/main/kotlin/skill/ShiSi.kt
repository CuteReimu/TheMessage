package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.ResolveResult
import com.fengsheng.phase.ReceivePhaseReceiverSkill
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Role.skill_shi_si_toc
import org.apache.log4j.Logger

/**
 * 老汉技能【视死】：你接收黑色情报后，摸两张牌。
 */
class ShiSi : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.SHI_SI

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm as? ReceivePhaseReceiverSkill
        if (fsm == null || fsm.inFrontOfWhom.findSkill(skillId) == null || !fsm.inFrontOfWhom.alive) return null
        if (fsm.inFrontOfWhom.getSkillUseCount(skillId) > 0) return null
        val colors = fsm.messageCard.colors
        if (!colors.contains(color.Black)) return null
        fsm.inFrontOfWhom.addSkillUseCount(skillId)
        log.info("${fsm.inFrontOfWhom}发动了[视死]")
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_shi_si_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(fsm.inFrontOfWhom.location)
                p.send(builder.build())
            }
        }
        fsm.inFrontOfWhom.draw(2)
        return null
    }

    companion object {
        private val log = Logger.getLogger(ShiSi::class.java)
    }
}