package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.protos.Common.card_type.Shi_Tan
import com.fengsheng.protos.Common.card_type.Wei_Bi
import com.fengsheng.protos.skillChengFuToc
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
            g.players.send { player ->
                skillChengFuToc {
                    playerId = player.getAlternativeLocation(askWhom.location)
                    fromPlayerId = player.getAlternativeLocation(event.player.location)
                    event.card?.let {
                        if (event.cardType != Shi_Tan || player === event.player)
                            card = it.toPbCard()
                        else
                            unknownCardCount = 1
                    }
                    cardType = event.cardType
                }
            }
        }
        event.valid = false
        return null
    }
}
