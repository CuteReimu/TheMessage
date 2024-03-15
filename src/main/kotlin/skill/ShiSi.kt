package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.protos.skillShiSiToc
import org.apache.logging.log4j.kotlin.logger

/**
 * 老汉技能【视死】：你接收黑色情报后，摸两张牌。
 */
class ShiSi : TriggeredSkill {
    override val skillId = SkillId.SHI_SI

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<ReceiveCardEvent>(this) { event ->
            askWhom === event.inFrontOfWhom || return@findEvent false
            event.inFrontOfWhom.getSkillUseCount(skillId) == 0 || return@findEvent false
            event.messageCard.isBlack()
        } ?: return null
        logger.info("${event.inFrontOfWhom}发动了[视死]")
        g.players.send { skillShiSiToc { playerId = it.getAlternativeLocation(event.inFrontOfWhom.location) } }
        event.inFrontOfWhom.draw(2)
        return null
    }
}