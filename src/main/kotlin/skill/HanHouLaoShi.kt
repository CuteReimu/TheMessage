package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.protos.Role.skill_han_hou_lao_shi_toc
import org.apache.log4j.Logger

/**
 * 哑炮技能【憨厚老实】：你的回合，你无法主动传出纯黑色情报（除非你只能传出纯黑色情报），接收你情报的玩家抽取你一张手牌。
 */
class HanHouLaoShi : TriggeredSkill, SendMessageCardSkill {
    override val skillId = SkillId.HAN_HOU_LAO_SHI

    override val isInitialSkill = true

    override fun checkSendCard(player: Player, whoseTurn: Player, availableCards: List<Card>, card: Card) =
        player !== whoseTurn || !card.isPureBlack() || availableCards.all { it.isPureBlack() }

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<ReceiveCardEvent>(this) { event ->
            askWhom === event.sender || return@findEvent false
            askWhom !== event.inFrontOfWhom || return@findEvent false
            askWhom.cards.isNotEmpty()
        } ?: return null
        val target = event.inFrontOfWhom
        val card = askWhom.cards.random()
        log.info("${askWhom}发动了[憨厚老实]，被${target}抽取了一张${card}")
        askWhom.deleteCard(card.id)
        target.cards.add(card)
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_han_hou_lao_shi_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(askWhom.location)
                builder.targetPlayerId = p.getAlternativeLocation(target.location)
                builder.card = card.toPbCard()
                p.send(builder.build())
            }
        }
        return null
    }

    companion object {
        private val log = Logger.getLogger(HanHouLaoShi::class.java)
    }
}