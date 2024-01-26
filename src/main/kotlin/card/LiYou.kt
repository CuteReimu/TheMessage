package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.ResolveCard
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Common.secret_task.*
import com.fengsheng.protos.Fengsheng.use_li_you_toc
import com.fengsheng.skill.cannotPlayCard
import org.apache.logging.log4j.kotlin.logger
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
        if (r.cannotPlayCard(type)) {
            logger.error("你被禁止使用利诱")
            (r as? HumanPlayer)?.sendErrorMessage("你被禁止使用利诱")
            return false
        }
        val target = args[0] as Player
        return Companion.canUse(g, r, target)
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val target = args[0] as Player
        logger.info("${r}对${target}使用了$this")
        r.deleteCard(id)
        execute(this, g, r, target)
    }

    override fun toString(): String {
        return "${cardColorToString(colors)}利诱"
    }

    companion object {
        fun canUse(g: Game, r: Player, target: Player): Boolean {
            if (r !== (g.fsm as? MainPhaseIdle)?.whoseTurn) {
                logger.error("利诱的使用时机不对")
                (r as? HumanPlayer)?.sendErrorMessage("利诱的使用时机不对")
                return false
            }
            if (!target.alive) {
                logger.error("目标已死亡")
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
            val fsm = g.fsm as MainPhaseIdle
            val resolveFunc = { _: Boolean ->
                val deckCards = g.deck.draw(1)
                var joinIntoHand = false
                if (deckCards.isNotEmpty()) {
                    if (target.checkThreeSameMessageCard(deckCards[0])) {
                        joinIntoHand = true
                        r.cards.addAll(deckCards)
                        logger.info("${deckCards.contentToString()}加入了${r}的手牌")
                    } else {
                        target.messageCards.addAll(deckCards)
                        logger.info("${deckCards.contentToString()}加入了${target}的的情报区")
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
                if (!joinIntoHand) r.game!!.addEvent(AddMessageCardEvent(r, false))
                OnFinishResolveCard(r, r, target, card?.getOriginCard(), card_type.Li_You, fsm)
            }
            g.resolve(ResolveCard(r, r, target, card?.getOriginCard(), card_type.Li_You, resolveFunc, fsm))
        }

        fun ai(e: MainPhaseIdle, card: Card): Boolean {
            val player = e.whoseTurn
            !player.cannotPlayCard(card_type.Li_You) || return false
            val game = player.game!!
            val nextCard = game.deck.peek(1).firstOrNull()
            if (player.identity == Black && player.secretTask == Disturber) {
                val players =
                    if (nextCard == null)
                        emptyList()
                    else
                        game.players.filter { it!!.alive && it !== player && it.messageCards.countTrueCard() < 2 }.run {
                            filter {
                                for (c in nextCard.colors) {
                                    when (c) {
                                        Red -> if (it!!.identity == Red || it.identity == Black &&
                                            it.secretTask in listOf(Collector, Mutator)
                                        ) return@filter false

                                        Blue -> if (it!!.identity == Blue || it.identity == Black &&
                                            it.secretTask in listOf(Collector, Mutator)
                                        ) return@filter false

                                        else -> if (it!!.identity == Black &&
                                            it.secretTask in listOf(Killer, Pioneer, Sweeper)
                                        ) return@filter false
                                    }
                                }
                                true
                            }.ifEmpty { this }
                        }
                val p = players.ifEmpty { game.players.filter { it !== player && it!!.alive } }
                    .randomOrNull() ?: return false
                GameExecutor.post(game, { card.execute(game, player, p) }, 2, TimeUnit.SECONDS)
            } else {
                val players =
                    if (nextCard == null || nextCard.colors.size == 2 && nextCard.isBlack()) {
                        game.players.filter { it!!.alive }
                    } else {
                        val (partners, enemies) = game.players.filter { it!!.alive }
                            .partition { player.isPartnerOrSelf(it!!) }
                        if (nextCard.isBlack()) enemies else partners
                    }
                val p = players.randomOrNull() ?: return false
                GameExecutor.post(game, { card.execute(game, player, p) }, 2, TimeUnit.SECONDS)
            }
            return true
        }
    }
}