package com.fengsheng.card

import com.fengsheng.Game
import com.fengsheng.GameExecutor
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.OnAddMessageCard
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Fengsheng.use_li_you_toc
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

class LiYou : Card {
    constructor(id: Int, colors: List<color>, direction: direction, lockable: Boolean) :
            super(id, colors, direction, lockable)

    constructor(id: Int, card: Card) : super(id, card)

    /**
     * 仅用于“作为利诱使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type = card_type.Li_You

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
            log.error("利诱被禁止使用了")
            (r as? HumanPlayer)?.sendErrorMessage("利诱被禁止使用了")
            return false
        }
        val target = args[0] as Player
        return Companion.canUse(g, r, target)
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val target = args[0] as Player
        log.info("${r}对${target}使用了$this")
        r.deleteCard(id)
        execute(this, g, r, target)
    }

    override fun toString(): String {
        return "${cardColorToString(colors)}利诱"
    }

    companion object {
        private val log = Logger.getLogger(LiYou::class.java)
        fun canUse(g: Game, r: Player, target: Player): Boolean {
            if (r !== (g.fsm as? MainPhaseIdle)?.player) {
                log.error("利诱的使用时机不对")
                (r as? HumanPlayer)?.sendErrorMessage("利诱的使用时机不对")
                return false
            }
            if (!target.alive) {
                log.error("目标已死亡")
                (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                return false
            }
            return true
        }

        /**
         * 执行【利诱】的效果
         *
         * @param card 使用的那张【利诱】卡牌。可以为 `null` ，因为肥原龙川技能【诡诈】可以视为使用了【利诱】。
         */
        fun execute(card: LiYou?, g: Game, r: Player, target: Player) {
            val resolveFunc = { _: Boolean ->
                val deckCards = g.deck.draw(1)
                var joinIntoHand = false
                if (deckCards.isNotEmpty()) {
                    if (target.checkThreeSameMessageCard(deckCards[0])) {
                        joinIntoHand = true
                        r.cards.addAll(deckCards)
                        log.info("${deckCards.contentToString()}加入了${r}的手牌")
                    } else {
                        target.messageCards.addAll(deckCards)
                        log.info("${deckCards.contentToString()}加入了${target}的的情报区")
                    }
                }
                for (player in g.players) {
                    if (player is HumanPlayer) {
                        val builder = use_li_you_toc.newBuilder()
                        builder.playerId = player.getAlternativeLocation(r.location)
                        builder.targetPlayerId = player.getAlternativeLocation(target.location)
                        if (card != null) builder.liYouCard = card.toPbCard()
                        builder.joinIntoHand = joinIntoHand
                        if (deckCards.isNotEmpty()) builder.messageCard = deckCards[0].toPbCard()
                        player.send(builder.build())
                    }
                }
                val newFsm =
                    OnFinishResolveCard(r, r, target, card?.getOriginCard(), card_type.Li_You, MainPhaseIdle(r))
                if (!joinIntoHand) OnAddMessageCard(r, newFsm, false)
                else newFsm
            }
            g.resolve(OnUseCard(r, r, target, card?.getOriginCard(), card_type.Li_You, resolveFunc, g.fsm!!))
        }

        fun ai(e: MainPhaseIdle, card: Card): Boolean {
            val player = e.player
            if (player === player.game!!.jinBiPlayer) return false
            if (player.game!!.qiangLingTypes.contains(card_type.Li_You)) return false
            if (player.location in player.game!!.diaoHuLiShanPlayers) return false
            val game = player.game!!
            val nextCard = game.deck.peek(1).firstOrNull()
            val players =
                if (nextCard == null || nextCard.colors.size == 2) {
                    game.players.filter { it!!.alive }
                } else {
                    val (partners, enemies) = game.players.filter { it!!.alive }
                        .partition { player.isPartnerOrSelf(it!!) }
                    if (nextCard.isBlack()) enemies else partners
                }
            val p = players.randomOrNull() ?: return false
            GameExecutor.post(game, { card.execute(game, player, p) }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}