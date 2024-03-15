package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.OnSendCard
import com.fengsheng.phase.ResolveCard
import com.fengsheng.phase.SendPhaseStart
import com.fengsheng.protos.Common.card_type.Yu_Qin_Gu_Zong
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.direction
import com.fengsheng.protos.useYuQinGuZongToc
import com.fengsheng.skill.cannotPlayCard
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

class YuQinGuZong : Card {
    constructor(id: Int, colors: List<color>, direction: direction, lockable: Boolean) :
            super(id, colors, direction, lockable)

    constructor(id: Int, card: Card) : super(id, card)

    /**
     * 仅用于“作为欲擒故纵使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type = Yu_Qin_Gu_Zong

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r.cannotPlayCard(type)) {
            logger.error("你被禁止使用欲擒故纵")
            r.sendErrorMessage("你被禁止使用欲擒故纵")
            return false
        }
        if (r !== (g.fsm as? SendPhaseStart)?.whoseTurn) {
            logger.error("欲擒故纵的使用时机不对")
            r.sendErrorMessage("欲擒故纵的使用时机不对")
            return false
        }
        return true
    }

    @Suppress("UNCHECKED_CAST")
    override fun execute(g: Game, r: Player, vararg args: Any) {
        val fsm = g.fsm as SendPhaseStart
        val messageCard = args[0] as Card
        val dir = args[1] as direction
        val target = args[2] as Player
        val lockPlayers = args[3] as List<Player>
        logger.info("${r}使用了$this，选择了${messageCard}传递")
        r.deleteCard(id)
        val resolveFunc = { _: Boolean ->
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    p.send(useYuQinGuZongToc {
                        card = toPbCard()
                        messageCardId = messageCard.id
                        playerId = p.getAlternativeLocation(r.location)
                        targetPlayerId = p.getAlternativeLocation(target.location)
                        lockPlayers.forEach { lockPlayerIds.add(p.getAlternativeLocation(it.location)) }
                    })
                }
            }
            r.deleteMessageCard(messageCard.id) // 欲擒故纵可能传出面前的情报
            r.draw(2)
            OnFinishResolveCard( // 这里先触发卡牌结算后，再触发情报传出时
                r, r, target, getOriginCard(), type, OnSendCard(
                    fsm.whoseTurn, fsm.whoseTurn, messageCard, dir, target, lockPlayers,
                    isMessageCardFaceUp = true, needRemoveCard = false, needNotify = false
                )
            )
        }
        g.resolve(ResolveCard(r, r, target, getOriginCard(), Yu_Qin_Gu_Zong, resolveFunc, fsm))
    }

    override fun toString(): String {
        return "${cardColorToString(colors)}欲擒故纵"
    }

    companion object {
        fun ai(e: SendPhaseStart, card: Card): Pair<Double, () -> Unit>? {
            val player = e.whoseTurn
            val game = player.game!!
            !player.cannotPlayCard(Yu_Qin_Gu_Zong) || return null
            val availableCards = player.messageCards.filter { !it.isPureBlack() }.ifEmpty { return null }
            var value = Double.NEGATIVE_INFINITY
            var result: SendMessageCardResult? = null
            for (messageCard in availableCards) {
                val v = player.calculateRemoveCardValue(player, player, messageCard)
                val rlt = player.calSendMessageCard(player, listOf(messageCard))
                if (v + rlt.value > value) {
                    value = v + rlt.value
                    result = rlt
                }
            }
            result ?: return null
            return (value + 20.0) to {
                GameExecutor.post(game, {
                    card.asCard(Yu_Qin_Gu_Zong)
                        .execute(game, player, result.card, result.dir, result.target, result.lockedPlayers.toList())
                }, 3, TimeUnit.SECONDS)
            }
        }
    }
}