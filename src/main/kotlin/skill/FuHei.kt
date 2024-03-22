package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.protos.skillFuHeiToc
import org.apache.logging.log4j.kotlin.logger

/**
 * 白菲菲技能【腹黑】：你传出的黑色情报被接收后，你摸一张牌。
 */
class FuHei : TriggeredSkill {
    override val skillId = SkillId.FU_HEI

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<ReceiveCardEvent>(this) { event ->
            askWhom === event.sender || return@findEvent false
            event.messageCard.isBlack()
        } ?: return null
        logger.info("${askWhom}发动了[腹黑]")
        g.players.send { skillFuHeiToc { playerId = it.getAlternativeLocation(event.sender.location) } }
        event.sender.draw(1)
        return null
    }
}
