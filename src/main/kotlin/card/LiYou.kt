package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.ResolveCard
import com.fengsheng.protos.Common.card_type.Li_You
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Common.direction
import com.fengsheng.protos.useLiYouToc
import com.fengsheng.skill.ConvertCardSkill
import com.fengsheng.skill.SkillId.HUO_XIN
import com.fengsheng.skill.SkillId.YUN_CHOU_WEI_WO
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

    override val type = Li_You

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r.cannotPlayCard(type)) {
            logger.error("你被禁止使用利诱")
            r.sendErrorMessage("你被禁止使用利诱")
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
                r.sendErrorMessage("利诱的使用时机不对")
                return false
            }
            if (!target.alive) {
                logger.error("目标已死亡")
                r.sendErrorMessage("目标已死亡")
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
                    if (target.checkThreeSameMessageCard(deckCards)) {
                        joinIntoHand = true
                        r.cards.addAll(deckCards)
                        logger.info("${deckCards.joinToString()}加入了${r}的手牌")
                    } else {
                        target.messageCards.addAll(deckCards)
                        val m = target.messageCards
                        logger.info("${deckCards.joinToString()}加入了${target}的的情报区，" +
                            "现在有${m.count(Red)}红${m.count(Blue)}蓝${m.count(Black)}黑")
                    }
                }
                g.players.send {
                    useLiYouToc {
                        playerId = it.getAlternativeLocation(r.location)
                        targetPlayerId = it.getAlternativeLocation(target.location)
                        if (card != null) liYouCard = card.toPbCard()
                        if (deckCards.isNotEmpty()) messageCard = deckCards[0].toPbCard()
                        this.joinIntoHand = joinIntoHand
                    }
                }
                if (!joinIntoHand) r.game!!.addEvent(AddMessageCardEvent(r, false))
                OnFinishResolveCard(r, r, target, card?.getOriginCard(), Li_You, fsm)
            }
            g.resolve(ResolveCard(r, r, target, card?.getOriginCard(), Li_You, resolveFunc, fsm))
        }

        fun ai(e: MainPhaseIdle, card: Card, convertCardSkill: ConvertCardSkill?): Boolean {
            val player = e.whoseTurn
            !player.cannotPlayCard(Li_You) || return false
            val game = player.game!!
            val nextCard = game.deck.peek(1).firstOrNull()
            var target: Player? = null
            if (nextCard != null && (player.getSkillUseCount(HUO_XIN) > 0 || player.getSkillUseCount(YUN_CHOU_WEI_WO) > 0)) {
                var value = 9
                for (p in game.sortedFrom(game.players, player.location)) {
                    p.alive || continue
                    val result = player.calculateMessageCardValue(player, p, nextCard, true)
                    if (result > value) {
                        value = result
                        target = p
                    }
                }
            } else {
                var value = 0.999
                for (p in game.sortedFrom(game.players, player.location)) {
                    p.alive || continue
                    val result = player.calculateMessageCardValue(player, p, true)
                    if (result > value) {
                        value = result
                        target = p
                    }
                }
            }
            target ?: return false
            GameExecutor.post(game, {
                convertCardSkill?.onConvert(player)
                card.asCard(Li_You).execute(game, player, target)
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}
