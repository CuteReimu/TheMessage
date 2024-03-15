package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.protos.Common.card_type.Shi_Tan
import com.fengsheng.protos.Common.card_type.Wei_Bi
import com.fengsheng.protos.skillShouKouRuPingToc
import org.apache.logging.log4j.kotlin.logger

/**
 * 哑炮技能【守口如瓶】：你对其他角色使用、其他角色对你使用【试探】和【威逼】时，这张牌无效。如果这是本回合首次触发此技能，双方各摸一张牌，否则你摸一张牌。
 */
class ShouKouRuPing : TriggeredSkill {
    override val skillId = SkillId.SHOU_KOU_RU_PING

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<UseCardEvent>(this) { event ->
            askWhom.alive || return@findEvent false
            event.cardType == Shi_Tan || event.cardType == Wei_Bi || return@findEvent false
            event.valid || return@findEvent false
            val targetPlayer = event.targetPlayer!!
            (askWhom === event.player || askWhom === targetPlayer) && event.player !== targetPlayer
        } ?: return null
        askWhom.addSkillUseCount(skillId)
        logger.info("${askWhom}发动了[守口如瓶]，${event.cardType}无效")
        if (event.valid) {
            askWhom.game!!.players.send { p ->
                skillShouKouRuPingToc {
                    playerId = p.getAlternativeLocation(askWhom.location)
                    cardPlayerId = p.getAlternativeLocation(event.player.location)
                    cardTargetPlayerId = p.getAlternativeLocation(event.targetPlayer!!.location)
                    cardType = event.cardType
                    event.card?.let { card ->
                        if (event.cardType != Shi_Tan || p === event.player)
                            this.card = card.toPbCard()
                        else
                            unknownCardCount = 1
                    }
                }
            }
        }
        if (askWhom.getSkillUseCount(skillId) == 1)
            g.sortedFrom(listOf(event.player, event.targetPlayer!!), event.whoseTurn.location).forEach { it.draw(1) }
        else
            askWhom.draw(1)
        event.valid = false
        return null
    }
}