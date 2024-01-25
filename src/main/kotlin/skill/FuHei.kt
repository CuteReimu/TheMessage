package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.protos.Role.skill_fu_hei_toc
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
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_fu_hei_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(event.sender.location)
                p.send(builder.build())
            }
        }
        event.sender.draw(1)
        return null
    }
}