package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.protos.Role.skill_han_hou_lao_shi_toc
import org.apache.logging.log4j.kotlin.logger

/**
 * 哑炮技能【憨厚老实】：其他角色接收你传出的情报后，抽取你一张牌。
 */
class HanHouLaoShi : TriggeredSkill {
    override val skillId = SkillId.HAN_HOU_LAO_SHI

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<ReceiveCardEvent>(this) { event ->
            askWhom === event.sender || return@findEvent false
            askWhom !== event.inFrontOfWhom || return@findEvent false
            askWhom.cards.isNotEmpty()
        } ?: return null
        val target = event.inFrontOfWhom
        val card = askWhom.cards.random()
        logger.info("${askWhom}发动了[憨厚老实]，被${target}抽取了一张${card}")
        askWhom.deleteCard(card.id)
        target.cards.add(card)
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_han_hou_lao_shi_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(askWhom.location)
                builder.targetPlayerId = p.getAlternativeLocation(target.location)
                if (askWhom === p || target === p) builder.card = card.toPbCard()
                p.send(builder.build())
            }
        }
        return null
    }
}