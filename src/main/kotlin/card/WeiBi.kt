package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Fengsheng.*
import com.fengsheng.protos.Role.skill_cheng_fu_toc
import com.fengsheng.protos.Role.skill_jiu_ji_b_toc
import com.fengsheng.skill.SkillId
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class WeiBi : Card {
    constructor(id: Int, colors: List<color>, direction: direction, lockable: Boolean) :
            super(id, colors, direction, lockable)

    constructor(id: Int, card: Card?) : super(id, card)

    /**
     * 仅用于“作为威逼使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type: card_type
        get() = card_type.Wei_Bi

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r === g.jinBiPlayer) {
            log.error("你被禁闭了，不能出牌")
            return false
        }
        if (g.qiangLingTypes.contains(type)) {
            log.error("威逼被禁止使用了")
            return false
        }
        val target = args[0] as Player
        val wantType = args[1] as card_type
        return Companion.canUse(g, r, target, wantType)
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val target = args[0] as Player
        val wantType = args[1] as card_type
        log.info("${r}对${target}使用了$this")
        r.deleteCard(id)
        execute(this, g, r, target, wantType)
    }

    private data class executeWeiBi(val r: Player, val target: Player, val card: WeiBi?, val wantType: card_type) :
        WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = wei_bi_wait_for_give_card_toc.newBuilder()
                    if (card != null) builder.card = card.toPbCard()
                    builder.setWantType(wantType).waitingSecond = 15
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    if (p === target) {
                        val seq2: Int = p.seq
                        builder.seq = seq2
                        p.timeout = GameExecutor.post(r.game!!, {
                            if (p.checkSeq(seq2)) {
                                p.incrSeq()
                                autoSelect()
                                r.game!!.resolve(MainPhaseIdle(r))
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    p.send(builder.build())
                }
            }
            if (target is RobotPlayer) {
                GameExecutor.post(r.game!!, {
                    autoSelect()
                    r.game!!.resolve(MainPhaseIdle(r))
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (message !is wei_bi_give_card_tos) {
                log.error("现在正在结算威逼")
                return null
            }
            if (target !== player) {
                log.error("你不是威逼的目标")
                return null
            }
            val cardId: Int = message.cardId
            val c = target.findCard(cardId)
            if (c == null) {
                log.error("没有这张牌")
                return null
            }
            target.incrSeq()
            log.info("${target}给了${r}一张$c")
            target.deleteCard(cardId)
            r.cards.add(c)
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = wei_bi_give_card_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    if (p === r || p === target) builder.card = c.toPbCard()
                    p.send(builder.build())
                }
            }
            if (card != null) r.game!!.deck.discard(card.getOriginCard())
            return ResolveResult(MainPhaseIdle(r), true)
        }

        private fun autoSelect() {
            val availableCards: MutableList<Int> = ArrayList()
            for (c in target.cards)
                if (c.type == wantType) availableCards.add(c.id)
            val cardId = availableCards[ThreadLocalRandom.current().nextInt(availableCards.size)]
            resolveProtocol(target, wei_bi_give_card_tos.newBuilder().setCardId(cardId).build())
        }

        companion object {
            private val log = Logger.getLogger(executeWeiBi::class.java)
        }
    }

    override fun toString(): String {
        return "${cardColorToString(colors)}威逼"
    }

    companion object {
        private val log = Logger.getLogger(WeiBi::class.java)
        fun canUse(g: Game, r: Player, target: Player, wantType: card_type): Boolean {
            if (r !== (g.fsm as? MainPhaseIdle)?.player) {
                log.error("威逼的使用时机不对")
                return false
            }
            if (r === target) {
                log.error("威逼不能对自己使用")
                return false
            }
            if (!target.alive) {
                log.error("目标已死亡")
                return false
            }
            if (!availableCardType.contains(wantType)) {
                log.error("威逼选择的卡牌类型错误：$wantType")
                return false
            }
            return true
        }

        /**
         * 执行【威逼】的效果
         *
         * @param card 使用的那张【威逼】卡牌。可以为 `null` ，因为肥原龙川技能【诡诈】可以视为使用了【威逼】。
         */
        fun execute(card: WeiBi?, g: Game, r: Player, target: Player, wantType: card_type) {
            val resolveFunc = object : Fsm {
                override fun resolve(): ResolveResult {
                    if (target.roleFaceUp && target.findSkill(SkillId.CHENG_FU) != null) {
                        log.info("${target}触发了[城府]，威逼无效")
                        for (player in g.players) {
                            if (player is HumanPlayer) {
                                val builder = skill_cheng_fu_toc.newBuilder()
                                builder.playerId = player.getAlternativeLocation(target.location)
                                builder.fromPlayerId = player.getAlternativeLocation(r.location)
                                if (card != null) builder.card = card.toPbCard()
                                player.send(builder.build())
                            }
                        }
                        if (target.getSkillUseCount(SkillId.JIU_JI) == 1) {
                            target.addSkillUseCount(SkillId.JIU_JI)
                            if (card != null) {
                                target.cards.add(card)
                                log.info("${target}将使用的${card}加入了手牌")
                                for (player in g.players) {
                                    if (player is HumanPlayer) {
                                        val builder = skill_jiu_ji_b_toc.newBuilder()
                                        builder.playerId = player.getAlternativeLocation(target.location)
                                        builder.card = card.toPbCard()
                                        player.send(builder.build())
                                    }
                                }
                            }
                        } else if (card != null) {
                            g.deck.discard(card.getOriginCard())
                        }
                        return ResolveResult(MainPhaseIdle(r), true)
                    }
                    if (hasCard(target, wantType)) {
                        return ResolveResult(executeWeiBi(r, target, card, wantType), true)
                    } else {
                        log.info("${target}向${r}展示了所有手牌")
                        if (card != null) g.deck.discard(card)
                        for (p in g.players) {
                            if (p is HumanPlayer) {
                                val builder = wei_bi_show_hand_card_toc.newBuilder()
                                if (card != null) builder.card = card.toPbCard()
                                builder.wantType = wantType
                                builder.playerId = p.getAlternativeLocation(r.location)
                                builder.targetPlayerId = p.getAlternativeLocation(target.location)
                                if (p === r) {
                                    for (c in target.cards) builder.addCards(c.toPbCard())
                                }
                                p.send(builder.build())
                            }
                        }
                        return ResolveResult(MainPhaseIdle(r), true)
                    }
                }
            }
            g.resolve(OnUseCard(r, r, target, card, card_type.Wei_Bi, r, resolveFunc))
        }

        private fun hasCard(player: Player, cardType: card_type): Boolean {
            for (card in player.cards) if (card.type == cardType) return true
            return false
        }

        private val availableCardType =
            listOf(card_type.Cheng_Qing, card_type.Jie_Huo, card_type.Diao_Bao, card_type.Wu_Dao)

        fun ai(e: MainPhaseIdle, card: Card): Boolean {
            val player = e.player
            val identity = player.identity
            val players = player.game!!.players.filter {
                it !== player && it!!.alive &&
                        (!it.roleFaceUp || it.findSkill(SkillId.CHENG_FU) == null) &&
                        (identity == color.Black || identity != it.identity) &&
                        it.cards.any { card -> availableCardType.contains(card.type) }
            }
            if (players.isEmpty()) return false
            val p = players[Random.nextInt(players.size)]!!
            val cardTypes = availableCardType.filter { cardType -> p.cards.any { it.type == cardType } }
            val cardType = cardTypes[Random.nextInt(cardTypes.size)]
            GameExecutor.post(player.game!!, { card.execute(player.game!!, player, p, cardType) }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}