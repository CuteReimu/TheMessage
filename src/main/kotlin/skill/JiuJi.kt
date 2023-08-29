package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.ResolveResult
import com.fengsheng.card.Card
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common.card_type.*
import com.fengsheng.protos.Role.skill_jiu_ji_a_toc
import com.fengsheng.protos.Role.skill_jiu_ji_b_toc
import org.apache.log4j.Logger

/**
 * 李宁玉技能【就计】：你被【试探】【威逼】或【利诱】指定为目标后，你可以翻开此角色牌，然后摸两张牌，并在触发此技能的卡牌结算后，将其加入你的手牌。
 */
class JiuJi : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.JIU_JI

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm as? OnUseCard ?: return null
        fsm.askWhom === fsm.targetPlayer || return null
        fsm.askWhom.alive || return null
        fsm.askWhom.findSkill(skillId) != null || return null
        fsm.cardType in cardTypes || return null
        !fsm.askWhom.roleFaceUp || return null
        fsm.askWhom.addSkillUseCount(skillId)
        log.info("${fsm.askWhom}发动了[就计]")
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_jiu_ji_a_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(fsm.askWhom.location)
                p.send(builder.build())
            }
        }
        g.playerSetRoleFaceUp(fsm.askWhom, true)
        fsm.askWhom.draw(2)
        fsm.card?.let {
            val skill = JiuJi2(it)
            skill.init(g)
            fsm.askWhom.skills = arrayOf(*fsm.askWhom.skills, skill)
        }
        return null
    }

    private class JiuJi2(val card: Card) : TriggeredSkill {
        override val skillId = SkillId.JIU_JI2

        override fun execute(g: Game): ResolveResult? {
            val fsm = g.fsm as? OnFinishResolveCard ?: return null
            fsm.askWhom === fsm.targetPlayer || return null
            fsm.askWhom.cards.add(card.getOriginCard())
            log.info("${fsm.askWhom}将使用的${card.getOriginCard()}加入了手牌")
            fsm.askWhom.skills = fsm.askWhom.skills.filterNot { it.skillId == skillId }.toTypedArray()
            val listeningSkills = g.listeningSkills
            listeningSkills.removeAt(listeningSkills.indexOfLast { it.skillId == skillId })
            for (player in g.players) {
                if (player is HumanPlayer) {
                    val builder = skill_jiu_ji_b_toc.newBuilder()
                    builder.playerId = player.getAlternativeLocation(fsm.askWhom.location)
                    builder.card = card.toPbCard()
                    player.send(builder.build())
                }
            }
            return ResolveResult(fsm.copy(whereToGoFunc = {}), true)
        }

        companion object {
            private val log = Logger.getLogger(JiuJi::class.java)
        }
    }

    companion object {
        private val log = Logger.getLogger(JiuJi::class.java)

        private val cardTypes = listOf(Shi_Tan, Wei_Bi, Li_You)
    }
}