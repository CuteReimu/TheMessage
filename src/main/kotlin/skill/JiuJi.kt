package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.ResolveResult
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common.card_type.*
import com.fengsheng.protos.Role.skill_jiu_ji_a_toc
import org.apache.log4j.Logger

/**
 * 李宁玉技能【就计】：你被【试探】【威逼】或【利诱】指定为目标后，你可以翻开此角色牌，然后摸两张牌，并在触发此技能的卡牌结算后，将其加入你的手牌。
 */
class JiuJi : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.JIU_JI

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm as? OnUseCard ?: return null
        fsm.askWhom.findSkill(skillId) != null || return null
        fsm.askWhom.alive || return null
        fsm.cardType in cardTypes || return null
        fsm.targetPlayer === fsm.askWhom || return null
        !fsm.askWhom.roleFaceUp || return null
        fsm.askWhom.addSkillUseCount(skillId)
        log.info("${fsm.askWhom}发动了[就计]")
        for (p in g.players) {
            (p as? HumanPlayer)?.send(
                skill_jiu_ji_a_toc.newBuilder().setPlayerId(p.getAlternativeLocation(fsm.askWhom.location)).build()
            )
        }
        g.playerSetRoleFaceUp(fsm.askWhom, true)
        fsm.askWhom.draw(2)
        return null
    }

    companion object {
        private val log = Logger.getLogger(JiuJi::class.java)

        private val cardTypes = listOf(Shi_Tan, Wei_Bi, Li_You)
    }
}