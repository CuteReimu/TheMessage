package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.card.WeiBiimport

com.fengsheng.card.WeiBi.executeWeiBiimport com.fengsheng.phase.MainPhaseIdleimport com.fengsheng.phase.OnUseCardimport com.fengsheng.protos.Common.*import com.fengsheng.protos.Fengshengimport

com.fengsheng.protos.Fengsheng.*import com.fengsheng.protos.Role.skill_cheng_fu_tocimport

com.fengsheng.protos.Role.skill_jiu_ji_b_tocimport com.fengsheng.skill.Skillimport com.fengsheng.skill.SkillIdimport com.google.protobuf.GeneratedMessageV3import org.apache.log4j.Loggerimport java.util.Listimport java.util.concurrent.*
class WeiBi : Card {
    constructor(id: Int, colors: Array<color>, direction: direction, lockable: Boolean) : super(
        id,
        colors,
        direction,
        lockable
    )

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
        log.info(r.toString() + "对" + target + "使用了" + this)
        r.deleteCard(id)
        execute(this, g, r, target, wantType)
    }

    private class executeWeiBi(r: Player, target: Player, val card: WeiBi?, wantType: card_type) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in r.game.players) {
                if (p is HumanPlayer) {
                    val builder = wei_bi_wait_for_give_card_toc.newBuilder()
                    if (card != null) builder.card = card.toPbCard()
                    builder.setWantType(wantType).waitingSecond = 15
                    builder.playerId = p.getAlternativeLocation(r.location())
                    builder.targetPlayerId = p.getAlternativeLocation(target.location())
                    if (p === target) {
                        val seq2: Int = p.seq
                        builder.seq = seq2
                        p.setTimeout(GameExecutor.Companion.post(r.game, Runnable {
                            if (p.checkSeq(seq2)) {
                                p.incrSeq()
                                autoSelect()
                                r.game.resolve(MainPhaseIdle(r))
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS))
                    }
                    p.send(builder.build())
                }
            }
            if (target is RobotPlayer) {
                GameExecutor.Companion.post(r.game, Runnable {
                    autoSelect()
                    r.game.resolve(MainPhaseIdle(r))
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player1: Player, message: GeneratedMessageV3): ResolveResult? {
            if (message !is Fengsheng.wei_bi_give_card_tos) {
                log.error("现在正在结算威逼")
                return null
            }
            if (target !== player1) {
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
            log.info(target.toString() + "给了" + r + "一张" + c)
            target.deleteCard(cardId)
            r.addCard(c)
            for (p in r.game.players) {
                if (p is HumanPlayer) {
                    val builder = wei_bi_give_card_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location())
                    builder.targetPlayerId = p.getAlternativeLocation(target.location())
                    if (p === r || p === target) builder.card = c.toPbCard()
                    p.send(builder.build())
                }
            }
            if (card != null) r.game.deck.discard(card.originCard)
            return ResolveResult(MainPhaseIdle(r), true)
        }

        private fun autoSelect() {
            val availableCards: MutableList<Int> = ArrayList()
            for (c in target.cards.values) if (c.type == wantType) availableCards.add(c.getId())
            val cardId = availableCards[ThreadLocalRandom.current().nextInt(availableCards.size)]
            resolveProtocol(target, Fengsheng.wei_bi_give_card_tos.newBuilder().setCardId(cardId).build())
        }

        val r: Player
        val target: Player
        val wantType: card_type

        init {
            this.sendPhase = sendPhase
            this.r = r
            this.target = target
            card = card
            this.wantType = wantType
        }

        companion object {
            private val log = Logger.getLogger(executeWeiBi::class.java)
        }
    }

    override fun toString(): String {
        return Card.Companion.cardColorToString(colors) + "威逼"
    }

    companion object {
        private val log = Logger.getLogger(WeiBi::class.java)
        fun canUse(g: Game, r: Player, target: Player, wantType: card_type): Boolean {
            if (g.fsm !is MainPhaseIdle || r !== fsm.player) {
                log.error("威逼的使用时机不对")
                return false
            }
            if (r === target) {
                log.error("威逼不能对自己使用")
                return false
            }
            if (!target.isAlive) {
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
            val resolveFunc = Fsm {
                if (target.isRoleFaceUp && target.findSkill<Skill?>(SkillId.CHENG_FU) != null) {
                    log.info(target.toString() + "触发了[城府]，威逼无效")
                    for (player in g.players) {
                        if (player is HumanPlayer) {
                            val builder = skill_cheng_fu_toc.newBuilder()
                                .setPlayerId(player.getAlternativeLocation(target.location()))
                            builder.fromPlayerId = player.getAlternativeLocation(r.location())
                            if (card != null) builder.card = card.toPbCard()
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
                    } else if (card != null) {
                        g.deck.discard(card.originCard)
                    }
                    return@Fsm ResolveResult(MainPhaseIdle(r), true)
                }
                if (hasCard(target, wantType)) {
                    return@Fsm ResolveResult(executeWeiBi(r, target, card, wantType), true)
                } else {
                    log.info(target.toString() + "向" + r + "展示了所有手牌")
                    if (card != null) g.deck.discard(card)
                    for (p in g.players) {
                        if (p is HumanPlayer) {
                            val builder = wei_bi_show_hand_card_toc.newBuilder()
                            if (card != null) builder.card = card.toPbCard()
                            builder.wantType = wantType
                            builder.playerId = p.getAlternativeLocation(r.location())
                            builder.targetPlayerId = p.getAlternativeLocation(target.location())
                            if (p === r) {
                                for (c in target.cards.values) builder.addCards(c.toPbCard())
                            }
                            p.send(builder.build())
                        }
                    }
                    return@Fsm ResolveResult(MainPhaseIdle(r), true)
                }
            }
            g.resolve(OnUseCard(r, r, target, card, card_type.Wei_Bi, r, resolveFunc))
        }

        private fun hasCard(player: Player, cardType: card_type): Boolean {
            for (card in player.cards.values) if (card.type == cardType) return true
            return false
        }

        private val availableCardType =
            List.of(card_type.Cheng_Qing, card_type.Jie_Huo, card_type.Diao_Bao, card_type.Wu_Dao)

        fun ai(e: MainPhaseIdle, card: Card): Boolean {
            val player = e.player
            val identity = player.identity
            val players: MutableList<Player> = ArrayList()
            for (p in player.game.players) {
                if (p !== player && p.isAlive && !p.cards.isEmpty() && (identity == color.Black || identity != p.identity)
                    && (!p.isRoleFaceUp || p.findSkill<Skill?>(SkillId.CHENG_FU) == null)
                ) {
                    for (c in p.cards.values) if (availableCardType.contains(c.type)) players.add(p)
                }
            }
            if (players.isEmpty()) return false
            val p = players[ThreadLocalRandom.current().nextInt(players.size)]
            val cardTypes: MutableList<card_type?> = ArrayList()
            for (c in p.cards.values) if (availableCardType.contains(c.type)) cardTypes.add(c.type)
            val cardType = cardTypes[ThreadLocalRandom.current().nextInt(cardTypes.size)]
            GameExecutor.Companion.post(
                player.game,
                Runnable { card.execute(player.game, player, p, cardType!!) },
                2,
                TimeUnit.SECONDS
            )
            return true
        }
    }
}