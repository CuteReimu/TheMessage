package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.protos.Common.card_type.Shi_Tan
import com.fengsheng.protos.Common.card_type.Wei_Bi
import com.fengsheng.protos.Role.skill_cheng_fu_toc
import org.apache.logging.log4j.kotlin.logger

/**
 * 李宁玉技能【城府】：【试探】和【威逼】对你无效。
 */
class ChengFu : TriggeredSkill {
    override val skillId = SkillId.CHENG_FU

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<UseCardEvent>(this) { event ->
            event.cardType == Shi_Tan || event.cardType == Wei_Bi || return@findEvent false
            askWhom === event.targetPlayer || return@findEvent false
            askWhom.roleFaceUp
        } ?: return null
        logger.info("${askWhom}触发了[城府]，${event.cardType}无效")
        if (event.valid) {
            for (player in g.players) {
                if (player is HumanPlayer) {
                    val builder = skill_cheng_fu_toc.newBuilder()
                    builder.playerId = player.getAlternativeLocation(askWhom.location)
                    builder.fromPlayerId = player.getAlternativeLocation(event.player.location)
                    event.card?.let { card ->
                        if (event.cardType != Shi_Tan || player === event.player)
                            builder.card = card.toPbCard()
                        else
                            builder.unknownCardCount = 1
                    }
                    builder.cardType = event.cardType
                    player.send(builder.build())
                }
            }
        }
        event.valid = false
        return null
    }
}