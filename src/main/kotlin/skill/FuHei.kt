package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.ResolveResult
import com.fengsheng.phase.ReceivePhaseSkill
import com.fengsheng.protos.Role.skill_fu_hei_toc
import org.apache.log4j.Logger

/**
 * 白菲菲技能【腹黑】：你传出的黑色情报被接收后，你摸一张牌。
 */
class FuHei : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.FU_HEI

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm as? ReceivePhaseSkill ?: return null
        fsm.askWhom === fsm.sender || return null
        fsm.sender.findSkill(skillId) != null || return null
        fsm.sender.getSkillUseCount(skillId) == 0 || return null
        fsm.messageCard.isBlack() || return null
        fsm.sender.addSkillUseCount(skillId)
        log.info("${fsm.sender}发动了[腹黑]")
        for (p in g.players) {
            (p as? HumanPlayer)?.send(
                skill_fu_hei_toc.newBuilder().setPlayerId(p.getAlternativeLocation(fsm.sender.location)).build()
            )
        }
        fsm.sender.draw(1)
        return null
    }

    companion object {
        private val log = Logger.getLogger(FuHei::class.java)
    }
}