package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.protos.Common.color.Blue
import com.fengsheng.protos.Common.color.Red
import com.fengsheng.protos.Role.skill_zhen_li_toc
import org.apache.logging.log4j.kotlin.logger

/**
 * 李书云技能【真理】：每当你传出的真情报被其他玩家接收时，你可以摸两张牌，将此角色翻回背面。
 */
class ZhenLi : TriggeredSkill {
    override val skillId = SkillId.ZHEN_LI

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        g.findEvent<ReceiveCardEvent>(this) { event ->
            askWhom === event.sender || return@findEvent false
            askWhom !== event.inFrontOfWhom || return@findEvent false
            askWhom.roleFaceUp || return@findEvent false
            Red in event.messageCard.colors || Blue in event.messageCard.colors
        } ?: return null
        logger.info("${askWhom}发动了[真理]")
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_zhen_li_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(askWhom.location)
                p.send(builder.build())
            }
        }
        askWhom.draw(2)
        g.playerSetRoleFaceUp(askWhom, false)
        return null
    }
}