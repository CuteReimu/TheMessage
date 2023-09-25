package com.fengsheng.card

import com.fengsheng.Game
import com.fengsheng.GameExecutor
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Fengsheng.use_diao_bao_toc
import org.apache.log4j.Logger
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
        if (r === g.jinBiPlayer) {
            log.error("你被禁闭了，不能出牌")
            (r as? HumanPlayer)?.sendErrorMessage("你被禁闭了，不能出牌")
            return false
        }
        if (r.location in g.diaoHuLiShanPlayers) {
            log.error("你被调虎离山了，不能出牌")
            (r as? HumanPlayer)?.sendErrorMessage("你被调虎离山了，不能出牌")
            return false
        }
        if (type in g.qiangLingTypes) {
            log.error("调包被禁止使用了")
            (r as? HumanPlayer)?.sendErrorMessage("调包被禁止使用了")
            return false
        }
        if (r !== (g.fsm as? FightPhaseIdle)?.whoseFightTurn) {
            log.error("调包的使用时机不对")
            (r as? HumanPlayer)?.sendErrorMessage("调包的使用时机不对")
            return false
        }
        return true
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val fsm = g.fsm as FightPhaseIdle
        log.info("${r}使用了$this")
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
        g.resolve(OnUseCard(fsm.whoseTurn, r, null, getOriginCard(), card_type.Diao_Bao, resolveFunc, fsm))
    }

    override fun toString(): String {
        return "${cardColorToString(colors)}调包"
    }

    companion object {
        private val log = Logger.getLogger(DiaoBao::class.java)
        fun ai(e: FightPhaseIdle, card: Card): Boolean {
            val player = e.whoseFightTurn
            if (player === player.game!!.jinBiPlayer) return false
            if (player.game!!.qiangLingTypes.contains(card_type.Diao_Bao)) return false
            if (player.location in player.game!!.diaoHuLiShanPlayers) return false
            if (player.identity != color.Black && player.identity == e.inFrontOfWhom.identity) {
                if (card.getColorScore() <= e.messageCard.getColorScore()) return false
            } else {
                if (card.getColorScore() >= e.messageCard.getColorScore()) return false
            }
            GameExecutor.post(player.game!!, { card.execute(player.game!!, player) }, 2, TimeUnit.SECONDS)
            return true
        }

        private fun Card.getColorScore() = colors.sumOf { if (it == color.Black) -1 else 2 as Int }
    }
}