package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.phase.ReceivePhaseSkill
import com.fengsheng.protos.Role.skill_fu_hei_toc
import org.apache.log4j.Logger

/**
 * 白菲菲技能【腹黑】：你传出的黑色情报被接收后，你摸一张牌。
 */
class FuHei : InitialSkill, TriggeredSkill {
    override val skillId = SkillId.FU_HEI

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val fsm = g.fsm as? ReceivePhaseSkill ?: return null
        askWhom === fsm.sender || return null
        fsm.sender.findSkill(skillId) != null || return null
        fsm.sender.getSkillUseCount(skillId) == 0 || return null
        fsm.messageCard.isBlack() || return null
        fsm.sender.addSkillUseCount(skillId)
        log.info("${fsm.sender}发动了[腹黑]")
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_fu_hei_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(fsm.sender.location)
                p.send(builder.build())
            }
        }
        fsm.sender.draw(1)
        return null
    }

    companion object {
        private val log = Logger.getLogger(FuHei::class.java)
    }
}