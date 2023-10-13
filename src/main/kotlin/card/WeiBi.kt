package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.ResolveCard
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Fengsheng.*
import com.fengsheng.skill.SkillId
import com.fengsheng.skill.cannotPlayCard
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

class WeiBi : Card {
    constructor(id: Int, colors: List<color>, direction: direction, lockable: Boolean) :
            super(id, colors, direction, lockable)

    constructor(id: Int, card: Card) : super(id, card)

    /**
     * 仅用于“作为威逼使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type = card_type.Wei_Bi

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r.cannotPlayCard(type)) {
            log.error("你被禁止使用威逼")
            (r as? HumanPlayer)?.sendErrorMessage("你被禁止使用威逼")
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

    private data class executeWeiBi(
        val fsm: MainPhaseIdle,
        val r: Player,
        val target: Player,
        val card: WeiBi?,
        val wantType: card_type
    ) :
        WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = wei_bi_wait_for_give_card_toc.newBuilder()
                    if (card != null) builder.card = card.toPbCard()
                    builder.wantType = wantType
                    builder.waitingSecond = Config.WaitSecond
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    if (p === target) {
                        val seq = p.seq
                        builder.seq = seq
                        p.timeout = GameExecutor.post(r.game!!, {
                            if (p.checkSeq(seq)) {
                                autoSelect()
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    p.send(builder.build())
                }
            }
            if (target is RobotPlayer) {
                GameExecutor.post(r.game!!, {
                    autoSelect()
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (message !is wei_bi_give_card_tos) {
                log.error("现在正在结算威逼")
                (player as? HumanPlayer)?.sendErrorMessage("现在正在结算威逼")
                return null
            }
            if (target !== player) {
                log.error("你不是威逼的目标")
                (player as? HumanPlayer)?.sendErrorMessage("你不是威逼的目标")
                return null
            }
            val cardId: Int = message.cardId
            val c = target.findCard(cardId)
            if (c == null) {
                log.error("没有这张牌")
                (player as? HumanPlayer)?.sendErrorMessage("没有这张牌")
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
            r.game!!.addEvent(GiveCardEvent(r, target, r))
            return ResolveResult(
                OnFinishResolveCard(r, r, target, card?.getOriginCard(), card_type.Wei_Bi, fsm), true
            )
        }

        private fun autoSelect() {
            val card = target.cards.filter { it.type == wantType }.random()
            val builder = wei_bi_give_card_tos.newBuilder()
            builder.cardId = card.id
            if (target is HumanPlayer) builder.seq = target.seq
            target.game!!.tryContinueResolveProtocol(target, builder.build())
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
            if (r !== (g.fsm as? MainPhaseIdle)?.whoseTurn) {
                log.error("威逼的使用时机不对")
                (r as? HumanPlayer)?.sendErrorMessage("威逼的使用时机不对")
                return false
            }
            if (!target.alive) {
                log.error("目标已死亡")
                (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                return false
            }
            if (!availableCardType.contains(wantType)) {
                log.error("威逼选择的卡牌类型错误：$wantType")
                (r as? HumanPlayer)?.sendErrorMessage("威逼选择的卡牌类型错误：$wantType")
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
            val fsm = g.fsm as MainPhaseIdle
            val resolveFunc = { valid: Boolean ->
                if (!valid) {
                    OnFinishResolveCard(r, r, target, card?.getOriginCard(), card_type.Wei_Bi, fsm)
                } else if (hasCard(target, wantType)) {
                    executeWeiBi(fsm, r, target, card, wantType)
                } else {
                    log.info("${target}向${r}展示了所有手牌")
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
                    OnFinishResolveCard(r, r, target, card?.getOriginCard(), card_type.Wei_Bi, fsm)
                }
            }
            g.resolve(ResolveCard(r, r, target, card?.getOriginCard(), card_type.Wei_Bi, resolveFunc, fsm))
        }

        private fun hasCard(player: Player, cardType: card_type): Boolean {
            for (card in player.cards) if (card.type == cardType) return true
            return false
        }

        private val availableCardType =
            listOf(card_type.Cheng_Qing, card_type.Jie_Huo, card_type.Diao_Bao, card_type.Wu_Dao)

        fun ai(e: MainPhaseIdle, card: Card): Boolean {
            val player = e.whoseTurn
            !player.cannotPlayCard(card_type.Wei_Bi) || return false
            val yaPao = player.game!!.players.find {
                it!!.alive && it.findSkill(SkillId.SHOU_KOU_RU_PING) != null
            }
            if (yaPao === player) {
                val p = player.game!!.players.run {
                    filter { it!!.alive && it.isPartner(player) }.randomOrNull()
                        ?: filter { it !== player && it!!.alive }.randomOrNull()
                } ?: return false
                val cardType = availableCardType.random()
                GameExecutor.post(player.game!!, {
                    card.execute(player.game!!, player, p, cardType)
                }, 2, TimeUnit.SECONDS)
                return true
            } else if (yaPao != null && player.isPartner(yaPao)) {
                val cardType = availableCardType.random()
                GameExecutor.post(player.game!!, {
                    card.execute(player.game!!, player, yaPao, cardType)
                }, 2, TimeUnit.SECONDS)
                return true
            }
            val identity = player.identity
            val p = player.game!!.players.filter {
                it !== player && it!!.alive &&
                        (!it.roleFaceUp || (it.findSkill(SkillId.CHENG_FU) == null && it.findSkill(SkillId.SHOU_KOU_RU_PING) == null)) &&
                        (identity == color.Black || identity != it.identity) &&
                        it.cards.any { card -> availableCardType.contains(card.type) }
            }.randomOrNull() ?: return false
            val cardType = availableCardType.filter { cardType -> p.cards.any { it.type == cardType } }.random()
            GameExecutor.post(player.game!!, { card.execute(player.game!!, player, p, cardType) }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}