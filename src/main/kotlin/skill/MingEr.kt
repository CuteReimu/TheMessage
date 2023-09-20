package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.phase.ReceivePhaseSkill
import com.fengsheng.protos.Common.color.Blue
import com.fengsheng.protos.Common.color.Red
import com.fengsheng.protos.Role.skill_ming_er_toc
import org.apache.log4j.Logger

/**
 * 老鳖技能【明饵】：你传出的红色或蓝色情报被接收后，你和接收者各摸一张牌。
 */
class MingEr : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.MING_ER

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val fsm = g.fsm as? ReceivePhaseSkill ?: return null
        askWhom === fsm.sender || return null
        fsm.sender.findSkill(skillId) != null || return null
        fsm.sender.getSkillUseCount(skillId) == 0 || return null
        val colors = fsm.messageCard.colors
        Red in colors || Blue in colors || return null
        fsm.sender.addSkillUseCount(skillId)
        log.info("${fsm.sender}发动了[明饵]")
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_ming_er_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(fsm.sender.location)
                p.send(builder.build())
            }
        }
        if (fsm.sender === fsm.inFrontOfWhom) {
            fsm.sender.draw(2)
        } else {
            fsm.sender.draw(1)
            fsm.inFrontOfWhom.draw(1)
        }
        return null
    }

    companion object {
        private val log = Logger.getLogger(MingEr::class.java)
    }
}