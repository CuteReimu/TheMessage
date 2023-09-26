package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.phase.ReceivePhaseSkill
import com.fengsheng.protos.Role.skill_shi_si_toc
import org.apache.log4j.Logger

/**
 * 老汉技能【视死】：你接收黑色情报后，摸两张牌。
 */
class ShiSi : InitialSkill, TriggeredSkill {
    override val skillId = SkillId.SHI_SI

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val fsm = g.fsm as? ReceivePhaseSkill ?: return null
        askWhom === fsm.inFrontOfWhom || return null
        fsm.inFrontOfWhom.getSkillUseCount(skillId) == 0 || return null
        fsm.messageCard.isBlack() || return null
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