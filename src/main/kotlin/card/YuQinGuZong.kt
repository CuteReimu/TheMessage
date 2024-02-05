package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.OnSendCard
import com.fengsheng.phase.ResolveCard
import com.fengsheng.phase.SendPhaseStart
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Common.card_type.Yu_Qin_Gu_Zong
import com.fengsheng.protos.Common.color.Blue
import com.fengsheng.protos.Common.color.Red
import com.fengsheng.protos.Common.secret_task.Collector
import com.fengsheng.protos.Common.secret_task.Mutator
import com.fengsheng.protos.Fengsheng.use_yu_qin_gu_zong_toc
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
            (r as? HumanPlayer)?.sendErrorMessage("你被禁止使用欲擒故纵")
            return false
        }
        if (r !== (g.fsm as? SendPhaseStart)?.whoseTurn) {
            logger.error("欲擒故纵的使用时机不对")
            (r as? HumanPlayer)?.sendErrorMessage("欲擒故纵的使用时机不对")
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
                    val builder = use_yu_qin_gu_zong_toc.newBuilder()
                    builder.card = toPbCard()
                    builder.messageCardId = messageCard.id
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    lockPlayers.forEach { builder.addLockPlayerIds(p.getAlternativeLocation(it.location)) }
                    p.send(builder.build())
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

    override fun toPbCard(): card {
        val builder = card.newBuilder()
        builder.cardId = id
        builder.cardDir = direction
        builder.canLock = lockable
        builder.cardType = type
        builder.addAllCardColor(colors)
        return builder.build()
    }

    override fun toString(): String {
        return "${cardColorToString(colors)}欲擒故纵"
    }

    companion object {
        fun ai(e: SendPhaseStart, card: Card): Boolean {
            val player = e.whoseTurn
            val game = player.game!!
            !player.cannotPlayCard(Yu_Qin_Gu_Zong) || return false
            var canRed = true
            var canBlue = true
            when (player.identity) {
                Red -> if (player.messageCards.count(Red) >= 2) canRed = false
                Blue -> if (player.messageCards.count(Blue) >= 2) canBlue = false
                else -> if (player.secretTask in listOf(Collector, Mutator)) {
                    if (player.messageCards.count(Red) >= 2) canRed = false
                    if (player.messageCards.count(Blue) >= 2) canBlue = false
                }
            }
            canRed || canBlue || return false
            val availableCards = player.messageCards.filter {
                !it.isPureBlack() && (canRed || Red !in it.colors) && (canBlue || Blue !in it.colors)
            }.ifEmpty { return false }
            val result = player.calSendMessageCard(availableCards = availableCards)
            result.value >= 0 || return false
            GameExecutor.post(game, {
                card.asCard(Yu_Qin_Gu_Zong)
                    .execute(game, player, result.card, result.dir, result.target, result.lockedPlayers.toList())
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}