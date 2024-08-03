package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.bestCard
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.ResolveCard
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Common.card_type.*
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Fengsheng.wei_bi_give_card_tos
import com.fengsheng.protos.weiBiGiveCardToc
import com.fengsheng.protos.weiBiGiveCardTos
import com.fengsheng.protos.weiBiShowHandCardToc
import com.fengsheng.protos.weiBiWaitForGiveCardToc
import com.fengsheng.skill.*
import com.fengsheng.skill.SkillId.SHOU_KOU_RU_PING
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

class WeiBi : Card {
    constructor(id: Int, colors: List<color>, direction: direction, lockable: Boolean) :
        super(id, colors, direction, lockable)

    constructor(id: Int, card: Card) : super(id, card)

    /**
     * 仅用于“作为威逼使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type = Wei_Bi

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r.cannotPlayCard(type)) {
            logger.error("你被禁止使用威逼")
            r.sendErrorMessage("你被禁止使用威逼")
            return false
        }
        val target = args[0] as Player
        val wantType = args[1] as card_type
        return Companion.canUse(g, r, target, wantType)
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val target = args[0] as Player
        val wantType = args[1] as card_type
        logger.info("${r}对${target}使用了$this，并宣言了$wantType")
        r.deleteCard(id)
        execute(this, g, r, target, wantType)
    }

    private data class ExecuteWeiBi(
        val fsm: MainPhaseIdle,
        val r: Player,
        val target: Player,
        val card: WeiBi?,
        val wantType: card_type
    ) : WaitingFsm {
        override val whoseTurn
            get() = fsm.whoseTurn

        override fun resolve(): ResolveResult? {
            r.game!!.players.send { p ->
                weiBiWaitForGiveCardToc {
                    if (this@ExecuteWeiBi.card != null) card = this@ExecuteWeiBi.card.toPbCard()
                    wantType = this@ExecuteWeiBi.wantType
                    waitingSecond = Config.WaitSecond
                    playerId = p.getAlternativeLocation(r.location)
                    targetPlayerId = p.getAlternativeLocation(target.location)
                    if (p === target) {
                        val seq = p.seq
                        this.seq = seq
                        p.timeout = GameExecutor.post(r.game!!, {
                            if (p.checkSeq(seq)) {
                                autoSelect()
                            }
                        }, p.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                }
            }
            if (target is RobotPlayer) {
                GameExecutor.post(r.game!!, {
                    autoSelect(true)
                }, 1, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (message !is wei_bi_give_card_tos) {
                logger.error("现在正在结算威逼")
                player.sendErrorMessage("现在正在结算威逼")
                return null
            }
            if (target !== player) {
                logger.error("你不是威逼的目标")
                player.sendErrorMessage("你不是威逼的目标")
                return null
            }
            val cardId: Int = message.cardId
            val c = target.findCard(cardId)
            if (c == null) {
                logger.error("没有这张牌")
                player.sendErrorMessage("没有这张牌")
                return null
            }
            target.incrSeq()
            logger.info("${target}给了${r}一张$c")
            target.deleteCard(cardId)
            r.cards.add(c)
            r.game!!.players.send {
                weiBiGiveCardToc {
                    playerId = it.getAlternativeLocation(r.location)
                    targetPlayerId = it.getAlternativeLocation(target.location)
                    if (it === r || it === target) card = c.toPbCard()
                }
            }
            r.game!!.addEvent(GiveCardEvent(r, target, r))
            target.weiBiFailRate = 0
            return ResolveResult(
                OnFinishResolveCard(r, r, target, card?.getOriginCard(), Wei_Bi, fsm), true
            )
        }

        private fun autoSelect(isRobot: Boolean = false) {
            val card = target.cards.filter { it.type == wantType }.run {
                if (isRobot) bestCard(target.identity, true) else random()
            }
            target.game!!.tryContinueResolveProtocol(target, weiBiGiveCardTos {
                cardId = card.id
                if (target is HumanPlayer) seq = target.seq
            })
        }
    }

    override fun toString(): String {
        return "${cardColorToString(colors)}威逼"
    }

    companion object {
        fun canUse(g: Game, r: Player, target: Player, wantType: card_type): Boolean {
            if (r !== (g.fsm as? MainPhaseIdle)?.whoseTurn) {
                logger.error("威逼的使用时机不对")
                r.sendErrorMessage("威逼的使用时机不对")
                return false
            }
            if (r === target) {
                logger.error("威逼不能对自己使用")
                r.sendErrorMessage("威逼不能对自己使用")
                return false
            }
            if (!target.alive) {
                logger.error("目标已死亡")
                r.sendErrorMessage("目标已死亡")
                return false
            }
            if (!availableCardType.contains(wantType)) {
                logger.error("威逼选择的卡牌类型错误：$wantType")
                r.sendErrorMessage("威逼选择的卡牌类型错误：$wantType")
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
                    OnFinishResolveCard(r, r, target, card?.getOriginCard(), Wei_Bi, fsm)
                } else if (hasCard(target, wantType)) {
                    r.weiBiFailRate++
                    ExecuteWeiBi(fsm, r, target, card, wantType)
                } else {
                    r.weiBiFailRate = 0
                    logger.info("${target}向${r}展示了所有手牌")
                    g.players.send { p ->
                        weiBiShowHandCardToc {
                            if (card != null) this.card = card.toPbCard()
                            this.wantType = wantType
                            playerId = p.getAlternativeLocation(r.location)
                            targetPlayerId = p.getAlternativeLocation(target.location)
                            if (p === r) target.cards.forEach { cards.add(it.toPbCard()) }
                        }
                    }
                    OnFinishResolveCard(r, r, target, card?.getOriginCard(), Wei_Bi, fsm)
                }
            }
            g.resolve(ResolveCard(r, r, target, card?.getOriginCard(), Wei_Bi, resolveFunc, fsm))
        }

        private fun hasCard(player: Player, cardType: card_type): Boolean {
            for (card in player.cards) if (card.type == cardType) return true
            return false
        }

        val availableCardType = listOf(Cheng_Qing, Jie_Huo, Diao_Bao, Wu_Dao)

        fun ai(e: MainPhaseIdle, card: Card, convertCardSkill: ConvertCardSkill?): Boolean {
            val player = e.whoseTurn
            !player.cannotPlayCard(Wei_Bi) || return false
            !player.game!!.isEarly || return false
            val yaPao = player.game!!.players.find {
                it!!.alive && it.findSkill(SHOU_KOU_RU_PING) != null
            }
            if (yaPao === player) {
                val p = player.game!!.players.run {
                    filter { it!!.alive && it.isPartner(player) }.randomOrNull()
                        ?: filter { it !== player && it!!.alive }.randomOrNull()
                } ?: return false
                val cardType = availableCardType.random()
                GameExecutor.post(player.game!!, {
                    card.asCard(Wei_Bi).execute(player.game!!, player, p, cardType)
                }, 3, TimeUnit.SECONDS)
                return true
            } else if (yaPao != null && player.isPartner(yaPao) && yaPao.getSkillUseCount(SHOU_KOU_RU_PING) == 0) {
                val cardType = availableCardType.random()
                GameExecutor.post(player.game!!, {
                    card.asCard(Wei_Bi).execute(player.game!!, player, yaPao, cardType)
                }, 3, TimeUnit.SECONDS)
                return true
            }
            val p = player.game!!.players.filter {
                it !== player && it!!.alive &&
                    (!it.roleFaceUp || !it.skills.any { s -> s is ChengFu || s is ShouKouRuPing || s is CunBuBuRang }) &&
                    it.isEnemy(player) &&
                    it.cards.any { card -> card.type in availableCardType }
            }.run {
                filter { it!!.cards.any { card -> card.type in listOf(Jie_Huo, Wu_Dao, Diao_Bao) } }.ifEmpty { this }
                    .run { if (player.identity != Black) filter { it!!.identity != Black }.ifEmpty { this } else this }
            }.randomOrNull() ?: return false
            val cardType =
                if (player.weiBiFailRate > 0) listOf(Jie_Huo, Wu_Dao, Diao_Bao).random() // 威逼成功后一定纯随机
                else availableCardType.filter { cardType -> p.cards.any { it.type == cardType } }.run {
                    filter { it != Cheng_Qing }.ifEmpty { this }
                }.random()
            GameExecutor.post(player.game!!, {
                convertCardSkill?.onConvert(player)
                card.asCard(Wei_Bi).execute(player.game!!, player, p, cardType)
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}
