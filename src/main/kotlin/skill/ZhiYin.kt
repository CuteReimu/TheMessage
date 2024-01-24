package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.protos.Common.color.Blue
import com.fengsheng.protos.Common.color.Red
import com.fengsheng.protos.Role.skill_zhi_yin_toc
import org.apache.logging.log4j.kotlin.logger

/**
 * 程小蝶技能【知音】：你接收红色或蓝色情报后，你和传出者各摸一张牌
 */
class ZhiYin : TriggeredSkill {
    override val skillId = SkillId.ZHI_YIN

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<ReceiveCardEvent>(this) { event ->
            askWhom === event.inFrontOfWhom || return@findEvent false
            event.inFrontOfWhom.getSkillUseCount(skillId) == 0 || return@findEvent false
            val colors = event.messageCard.colors
            Red in colors || Blue in colors
        } ?: return null
        logger.info("${event.inFrontOfWhom}发动了[知音]")
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_zhi_yin_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(event.inFrontOfWhom.location)
                p.send(builder.build())
            }
        }
        if (event.inFrontOfWhom === event.sender) {
            event.inFrontOfWhom.draw(2)
        } else {
            val players = arrayListOf(event.inFrontOfWhom)
            if (event.sender.alive) players.add(event.sender)
            g.sortedFrom(players, event.whoseTurn.location).forEach { it.draw(1) }
        }
        return null
    }

    companion object {
    }
}