package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.card.LiYouimport

com.fengsheng.phase.MainPhaseIdleimport com.fengsheng.phase.OnUseCardimport com.fengsheng.protos.Common.*import com.fengsheng.protos.Fengsheng.use_li_you_tocimport

com.fengsheng.protos.Role.skill_jiu_ji_b_tocimport com.fengsheng.skill.SkillIdimport org.apache.log4j.Loggerimport java.util.*import java.util.concurrent.*

class LiYou : Card {
    constructor(id: Int, colors: Array<color>, direction: direction, lockable: Boolean) : super(
        id,
        colors,
        direction,
        lockable
    )

    constructor(id: Int, card: Card?) : super(id, card)

    /**
     * 仅用于“作为利诱使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type: card_type
        get() = card_type.Li_You

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r === g.jinBiPlayer) {
            log.error("你被禁闭了，不能出牌")
            return false
        }
        if (g.qiangLingTypes.contains(type)) {
            log.error("利诱被禁止使用了")
            return false
        }
        val target = args[0] as Player
        return Companion.canUse(g, r, target)
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val target = args[0] as Player
        log.info(r.toString() + "对" + target + "使用了" + this)
        r.deleteCard(id)
        execute(this, g, r, target)
    }

    override fun toString(): String {
        return Card.Companion.cardColorToString(colors) + "利诱"
    }

    companion object {
        private val log = Logger.getLogger(LiYou::class.java)
        fun canUse(g: Game, r: Player, target: Player): Boolean {
            if (g.fsm !is MainPhaseIdle || r !== fsm.player) {
                log.error("利诱的使用时机不对")
                return false
            }
            if (!target.isAlive) {
                log.error("目标已死亡")
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
            val resolveFunc = Fsm {
                val deckCards = g.deck.draw(1)
                var joinIntoHand = false
                if (deckCards.size > 0) {
                    if (target.checkThreeSameMessageCard(deckCards[0])) {
                        joinIntoHand = true
                        r.addCard(*deckCards)
                        log.info(Arrays.toString(deckCards) + "加入了" + r + "的手牌")
                    } else {
                        target.addMessageCard(*deckCards)
                        log.info(Arrays.toString(deckCards) + "加入了" + target + "的情报区")
                    }
                }
                for (player in g.players) {
                    if (player is HumanPlayer) {
                        val builder = use_li_you_toc.newBuilder()
                        builder.playerId = player.getAlternativeLocation(r.location())
                        builder.targetPlayerId = player.getAlternativeLocation(target.location())
                        if (card != null) builder.liYouCard = card.toPbCard()
                        builder.joinIntoHand = joinIntoHand
                        if (deckCards.size > 0) builder.messageCard = deckCards[0].toPbCard()
                        player.send(builder.build())
                    }
                }
                if (target.getSkillUseCount(SkillId.JIU_JI) == 1) {
                    target.addSkillUseCount(SkillId.JIU_JI)
                    if (card != null) {
                        target.addCard(card)
                        log.info(target.toString() + "将使用的" + card + "加入了手牌")
                        for (player in g.players) {
                            if (player is HumanPlayer) {
                                val builder = skill_jiu_ji_b_toc.newBuilder()
                                    .setPlayerId(player.getAlternativeLocation(target.location()))
                                builder.card = card.toPbCard()
                                player.send(builder.build())
                            }
                        }
                    }
                } else {
                    if (card != null) g.deck.discard(card.originCard)
                }
                ResolveResult(MainPhaseIdle(r), true)
            }
            if (card != null) g.resolve(OnUseCard(r, r, target, card, card_type.Li_You, r, resolveFunc)) else g.resolve(
                resolveFunc
            )
        }

        fun ai(e: MainPhaseIdle, card: Card): Boolean {
            val player = e.player
            val players: MutableList<Player> = ArrayList()
            for (p in player.game.players) if (p.isAlive) players.add(p)
            if (players.isEmpty()) return false
            val p = players[ThreadLocalRandom.current().nextInt(players.size)]
            GameExecutor.Companion.post(
                player.game,
                Runnable { card.execute(player.game, player, p) },
                2,
                TimeUnit.SECONDS
            )
            return true
        }
    }
}