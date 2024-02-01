package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.ResolveCard
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Common.color.Blue
import com.fengsheng.protos.Common.color.Red
import com.fengsheng.protos.Fengsheng.notify_phase_toc
import com.fengsheng.protos.Fengsheng.use_diao_bao_toc
import com.fengsheng.skill.cannotPlayCard
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

class DiaoBao : Card {
    constructor(id: Int, colors: List<color>, direction: direction, lockable: Boolean) :
            super(id, colors, direction, lockable)

    constructor(id: Int, card: Card) : super(id, card)

    /**
     * 仅用于“作为调包使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type = card_type.Diao_Bao

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r.cannotPlayCard(type)) {
            logger.error("你被禁止使用调包")
            (r as? HumanPlayer)?.sendErrorMessage("你被禁止使用调包")
            return false
        }
        if (r !== (g.fsm as? FightPhaseIdle)?.whoseFightTurn) {
            logger.error("调包的使用时机不对")
            (r as? HumanPlayer)?.sendErrorMessage("调包的使用时机不对")
            return false
        }
        return true
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val fsm = g.fsm as FightPhaseIdle
        logger.info("${r}使用了$this")
        r.deleteCard(id)
        val resolveFunc = { _: Boolean ->
            val oldCard = fsm.messageCard
            g.deck.discard(oldCard)
            for (player in g.players) {
                if (player is HumanPlayer) {
                    val builder = use_diao_bao_toc.newBuilder()
                    builder.oldMessageCard = oldCard.toPbCard()
                    builder.playerId = player.getAlternativeLocation(r.location)
                    if (player === r) builder.cardId = id
                    player.send(builder.build())
                }
            }
            val newFsm = fsm.copy(
                messageCard = getOriginCard(),
                isMessageCardFaceUp = false,
                whoseFightTurn = fsm.inFrontOfWhom
            )
            for (p in g.players) { // 解决客户端动画问题
                if (p is HumanPlayer) {
                    val builder = notify_phase_toc.newBuilder()
                    builder.currentPlayerId = p.getAlternativeLocation(newFsm.whoseTurn.location)
                    builder.messagePlayerId = p.getAlternativeLocation(newFsm.inFrontOfWhom.location)
                    builder.waitingPlayerId = p.getAlternativeLocation(newFsm.whoseFightTurn.location)
                    builder.currentPhase = phase.Fight_Phase
                    p.send(builder.build())
                }
            }
            OnFinishResolveCard(
                fsm.whoseTurn,
                r,
                null,
                getOriginCard(),
                card_type.Diao_Bao,
                newFsm,
                discardAfterResolve = false
            )
        }
        g.resolve(ResolveCard(fsm.whoseTurn, r, null, getOriginCard(), card_type.Diao_Bao, resolveFunc, fsm))
    }

    override fun toString(): String {
        return "${cardColorToString(colors)}调包"
    }

    companion object {
        fun ai(e: FightPhaseIdle, card: Card): Boolean {
            val player = e.whoseFightTurn
            !player.cannotPlayCard(card_type.Diao_Bao) || return false
            if (player.identity == Red) {
                if (Blue in card.colors) return false
            } else if (player.identity == Blue) {
                if (Red in card.colors) return false
            }
            val oldValue = player.calculateMessageCardValue(e.whoseTurn, e.inFrontOfWhom, e.messageCard)
            val newValue = player.calculateMessageCardValue(e.whoseTurn, e.inFrontOfWhom, card)
            newValue > oldValue || return false
            GameExecutor.post(player.game!!, { card.execute(player.game!!, player) }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}