package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.protos.Common.card_type.Shi_Tan
import com.fengsheng.protos.Common.card_type.Wei_Bi
import com.fengsheng.protos.Role.skill_shou_kou_ru_ping_toc
import org.apache.log4j.Logger

/**
 * 哑炮技能【守口如瓶】：你对其他角色使用、其他角色对你使用【试探】和【威逼】时，取消原效果，改为双方各摸一张牌。
 */
class ShouKouRuPing : InitialSkill, TriggeredSkill {
    override val skillId = SkillId.SHOU_KOU_RU_PING

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<UseCardEvent>(this) { event ->
            askWhom.alive || return@findEvent false
            event.cardType == Shi_Tan || event.cardType == Wei_Bi || return@findEvent false
            event.valid || return@findEvent false
            val targetPlayer = event.targetPlayer!!
            (askWhom === event.player || askWhom === targetPlayer) && event.player !== targetPlayer
        } ?: return null
        log.info("${askWhom}发动了[守口如瓶]")
        for (p in askWhom.game!!.players) {
            if (p is HumanPlayer) {
                val builder = skill_shou_kou_ru_ping_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(askWhom.location)
                builder.cardPlayerId = p.getAlternativeLocation(event.player.location)
                builder.cardTargetPlayerId = p.getAlternativeLocation(event.targetPlayer!!.location)
                builder.cardType = event.cardType
                event.card?.let { card ->
                    if (event.cardType != Shi_Tan || p === event.player)
                        builder.card = card.toPbCard()
                    else
                        builder.unknownCardCount = 1
                }
                p.send(builder.build())
            }
        }
        g.sortedFrom(listOf(event.player, event.targetPlayer!!), event.whoseTurn.location).forEach { it.draw(1) }
        event.valid = false
        return null
    }

    companion object {
        private val log = Logger.getLogger(ShouKouRuPing::class.java)
    }
}