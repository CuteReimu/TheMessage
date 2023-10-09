package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.phase.ReceivePhaseSkill
import com.fengsheng.protos.Common.color.Blue
import com.fengsheng.protos.Common.color.Red
import com.fengsheng.protos.Role.skill_zhen_li_toc
import org.apache.log4j.Logger

/**
 * 李书云技能【真理】：每当你传出的真情报被其他玩家接收时，你可以摸两张牌，将此角色翻回背面。
 */
class ZhenLi : InitialSkill, TriggeredSkill {
    override val skillId = SkillId.ZHEN_LI

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val fsm = g.fsm as? ReceivePhaseSkill ?: return null
        askWhom === fsm.sender || return null
        askWhom !== fsm.inFrontOfWhom || return null
        Red in fsm.messageCard.colors || Blue in fsm.messageCard.colors || return null
        askWhom.roleFaceUp || return null
        askWhom.addSkillUseCount(skillId)
        log.info("${askWhom}发动了[真理]")
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_zhen_li_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(askWhom.location)
                p.send(builder.build())
            }
        }
        askWhom.draw(2)
        g.playerSetRoleFaceUp(askWhom, false)
        return null
    }

    companion object {
        private val log = Logger.getLogger(ZhenLi::class.java)
    }
}