package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Common.card_type.Jie_Huo
import com.fengsheng.protos.Role.skill_shun_shi_er_wei_toc
import org.apache.log4j.Logger

/**
 * 成年小九技能【顺势而为】：你使用【截获】或者你面前的情报被【截获】后，可以将此角色牌翻回背面，摸一张牌。
 */
class ShunShiErWei : InitialSkill, TriggeredSkill {
    override val skillId = SkillId.SHUN_SHI_ER_WEI

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        g.findEvent<UseCardEvent>(this) { event ->
            askWhom.alive || return@findEvent false
            event.cardType == Jie_Huo || return@findEvent false
            askWhom.roleFaceUp || return@findEvent false
            askWhom === event.player || askWhom === (event.currentFsm as? FightPhaseIdle)?.inFrontOfWhom
        } ?: return null
        askWhom.skills += ShunShiErWei2()
        return null
    }

    private class ShunShiErWei2 : TriggeredSkill, OneTurnSkill {
        override val skillId = SkillId.UNKNOWN

        override fun execute(g: Game, askWhom: Player): ResolveResult? {
            g.findEvent<FinishResolveCardEvent>(this) {
                askWhom.alive
            } ?: return null
            log.info("${askWhom}发动了[顺势而为]")
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_shun_shi_er_wei_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(askWhom.location)
                    p.send(builder.build())
                }
            }
            askWhom.draw(1)
            g.playerSetRoleFaceUp(askWhom, false)
            return null
        }

        companion object {
            private val log = Logger.getLogger(ShunShiErWei2::class.java)
        }
    }
}