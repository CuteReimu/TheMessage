package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.sortCards
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.OnSendCard
import com.fengsheng.phase.ResolveCard
import com.fengsheng.phase.SendPhaseStart
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Common.card_type.Mi_Ling
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Fengsheng.*
import com.fengsheng.protos.Role
import com.fengsheng.skill.LengXueXunLian
import com.fengsheng.skill.SkillId.LENG_XUE_XUN_LIAN
import com.fengsheng.skill.canSendCard
import com.fengsheng.skill.cannotPlayCard
import com.google.protobuf.GeneratedMessageV3
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

class MiLing : Card {
    private val secret: List<color>

    constructor(id: Int, colors: List<color>, direction: direction, lockable: Boolean, secret: List<color>) :
            super(id, colors, direction, lockable) {
        this.secret = secret
    }

    constructor(id: Int, card: MiLing) : super(id, card) {
        this.secret = card.secret
    }

    override val type = Mi_Ling

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r.cannotPlayCard(type)) {
            logger.error("你被禁止使用密令")
            (r as? HumanPlayer)?.sendErrorMessage("你被禁止使用密令")
            return false
        }
        if (r !== (g.fsm as? SendPhaseStart)?.whoseTurn) {
            logger.error("密令的使用时机不对")
            (r as? HumanPlayer)?.sendErrorMessage("密令的使用时机不对")
            return false
        }
        val target = args[0] as Player
        if (r === target) {
            logger.error("密令不能对自己使用")
            (r as? HumanPlayer)?.sendErrorMessage("密令不能对自己使用")
            return false
        }
        if (!target.alive) {
            logger.error("目标已死亡")
            (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
            return false
        }
        if (target.cards.isEmpty()) {
            logger.error("目标没有手牌")
            (r as? HumanPlayer)?.sendErrorMessage("目标没有手牌")
            return false
        }
        return true
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val fsm = g.fsm as SendPhaseStart
        val target = args[0] as Player
        val secret = args[1] as Int
        val color = this.secret[secret]
        logger.info("${r}对${target}使用了$this，并宣言了$color")
        r.deleteCard(id)
        val hasColor = target.cards.any { color in it.colors }
        val timeout = Config.WaitSecond
        val resolveFunc = { _: Boolean ->
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = use_mi_ling_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.secret = secret
                    if (p === r || p === target) builder.card = toPbCard()
                    builder.hasColor = hasColor
                    builder.waitingSecond = timeout
                    if (!hasColor && p === r)
                        target.cards.forEach { builder.addHandCards(it.toPbCard()) }
                    if (hasColor && p === target || !hasColor && p === r) {
                        builder.seq = p.seq
                    }
                    p.send(builder.build())
                }
            }
            if (hasColor)
                executeMiLing(this@MiLing, target, secret, null, fsm, timeout)
            else
                miLingChooseCard(this@MiLing, r, target, secret, fsm, timeout)
        }
        g.resolve(ResolveCard(r, r, target, getOriginCard(), Mi_Ling, resolveFunc, fsm))
    }

    private data class miLingChooseCard(
        val card: MiLing,
        val player: Player,
        val target: Player,
        val secret: Int,
        val sendPhase: SendPhaseStart,
        val timeout: Int
    ) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = player
            if (r is HumanPlayer) {
                val seq2 = r.seq
                r.timeout = GameExecutor.post(r.game!!, {
                    if (r.checkSeq(seq2)) {
                        val builder = mi_ling_choose_card_tos.newBuilder()
                        builder.cardId = target.cards.random().id
                        builder.seq = seq2
                        r.game!!.tryContinueResolveProtocol(r, builder.build())
                    }
                }, r.getWaitSeconds(timeout + 2).toLong(), TimeUnit.SECONDS)
            } else {
                GameExecutor.post(r.game!!, {
                    val builder = mi_ling_choose_card_tos.newBuilder()
                    var value = Double.POSITIVE_INFINITY
                    var card = target.cards.first()
                    for (c in target.cards.sortCards(target.identity, true)) {
                        val v = target.calSendMessageCard(sendPhase.whoseTurn, listOf(c)).value
                        if (v <= value) {
                            value = v
                            card = c
                        }
                    }
                    builder.cardId = card.id
                    r.game!!.tryContinueResolveProtocol(r, builder.build())
                }, 3, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (message !is mi_ling_choose_card_tos) {
                logger.error("现在正在结算密令")
                (player as? HumanPlayer)?.sendErrorMessage("现在正在结算密令")
                return null
            }
            if (player !== this.player) {
                logger.error("没有轮到你操作")
                (player as? HumanPlayer)?.sendErrorMessage("没有轮到你操作")
                return null
            }
            val card = target.findCard(message.cardId)
            if (card == null) {
                logger.error("没有这张牌")
                (player as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                return null
            }
            player.incrSeq()
            val timeout = Config.WaitSecond
            for (p in player.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = mi_ling_choose_card_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(player.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.waitingSecond = timeout
                    if (p === player || p === target)
                        builder.card = card.toPbCard()
                    if (p === target)
                        builder.seq = p.seq
                    p.send(builder.build())
                }
            }
            return ResolveResult(executeMiLing(this.card, target, secret, card, sendPhase, timeout), true)
        }
    }

    data class executeMiLing(
        val card: MiLing,
        val target: Player,
        val secret: Int,
        val messageCard: Card?,
        val sendPhase: SendPhaseStart,
        val timeout: Int
    ) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            if (target is HumanPlayer) {
                val seq2 = target.seq
                target.timeout = GameExecutor.post(target.game!!, {
                    if (target.checkSeq(seq2)) {
                        val card = messageCard ?: target.cards.find { this.card.secret[secret] in it.colors }!!
                        val messageTarget = when (card.direction) {
                            direction.Left -> target.getNextLeftAlivePlayer()
                            direction.Right -> target.getNextRightAlivePlayer()
                            else -> target.game!!.players.filter { target !== it && it!!.alive }.random()!!
                        }
                        val builder = send_message_card_tos.newBuilder()
                        builder.cardId = card.id
                        builder.targetPlayerId = target.getAlternativeLocation(messageTarget.location)
                        builder.cardDir = card.direction
                        builder.seq = seq2
                        target.game!!.tryContinueResolveProtocol(target, builder.build())
                    }
                }, target.getWaitSeconds(timeout + 2).toLong(), TimeUnit.SECONDS)
            } else {
                GameExecutor.post(target.game!!, {
                    val skill = target.findSkill(LENG_XUE_XUN_LIAN) as? LengXueXunLian
                    if (skill != null) {
                        skill.executeProtocol(
                            target.game!!,
                            target,
                            Role.skill_leng_xue_xun_lian_a_tos.getDefaultInstance()
                        )
                    } else {
                        val availableCards =
                            if (messageCard != null) listOf(messageCard)
                            else target.cards.filter(this.card.secret[secret])
                        val result = target.calSendMessageCard(sendPhase.whoseTurn, availableCards)
                        val builder = send_message_card_tos.newBuilder()
                        builder.cardId = result.card.id
                        builder.targetPlayerId = target.getAlternativeLocation(result.target.location)
                        builder.cardDir = result.dir
                        builder.addAllLockPlayerId(result.lockedPlayers.map { target.getAlternativeLocation(it.location) })
                        target.game!!.tryContinueResolveProtocol(target, builder.build())
                    }
                }, 1, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            val pb = message as? send_message_card_tos
            if (pb == null) {
                logger.error("现在正在结算密令")
                (player as? HumanPlayer)?.sendErrorMessage("现在正在结算密令")
                return null
            }
            if (player !== target) {
                logger.error("没有轮到你传情报")
                (player as? HumanPlayer)?.sendErrorMessage("没有轮到你传情报")
                return null
            }
            val availableCards =
                if (this.messageCard != null) listOf(this.messageCard)
                else player.cards.filter(this.card.secret[secret])
            val messageCard = target.findCard(message.cardId)
            if (messageCard == null) {
                logger.error("没有这张牌")
                (player as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                return null
            }
            if (pb.targetPlayerId <= 0 || pb.targetPlayerId >= target.game!!.players.size) {
                logger.error("目标错误: ${pb.targetPlayerId}")
                (player as? HumanPlayer)?.sendErrorMessage("遇到了bug，试试把牌取消选择重新选一下")
                return null
            }
            val messageTarget = target.game!!.players[target.getAbstractLocation(pb.targetPlayerId)]!!
            val lockPlayers = pb.lockPlayerIdList.map {
                if (it < 0 || it >= target.game!!.players.size) {
                    logger.error("锁定目标错误: $it")
                    (player as? HumanPlayer)?.sendErrorMessage("锁定目标错误: $it")
                    return null
                }
                target.game!!.players[target.getAbstractLocation(it)]!!
            }
            val sendCardError = target.canSendCard(
                sendPhase.whoseTurn,
                messageCard,
                availableCards,
                pb.cardDir,
                messageTarget,
                lockPlayers
            )
            if (sendCardError != null) {
                logger.error(sendCardError)
                (player as? HumanPlayer)?.sendErrorMessage(sendCardError)
                return null
            }
            player.incrSeq()
            target.deleteCard(messageCard.id)
            val newFsm = OnSendCard(
                sendPhase.whoseTurn,
                target,
                messageCard,
                pb.cardDir,
                messageTarget,
                lockPlayers,
                needRemoveCard = false
            )
            return ResolveResult(
                OnFinishResolveCard(
                    sendPhase.whoseTurn, sendPhase.whoseTurn, target, card.getOriginCard(), Mi_Ling, newFsm,
                    discardAfterResolve = false
                ),
                true
            )
        }
    }

    override fun toPbCard(): card {
        val builder = card.newBuilder()
        builder.cardId = id
        builder.cardDir = direction
        builder.canLock = lockable
        builder.cardType = type
        builder.addAllCardColor(colors)
        builder.addAllSecretColor(secret)
        return builder.build()
    }

    override fun toString(): String {
        return "${cardColorToString(colors)}密令"
    }

    companion object {
        fun ai(e: SendPhaseStart, card: Card): Pair<Double, () -> Unit>? {
            card as MiLing
            val player = e.whoseTurn
            !player.cannotPlayCard(Mi_Ling) || return null
            var value = Double.NEGATIVE_INFINITY
            var target: Player? = null
            var color = Black
            for (p in player.game!!.players) {
                p!!.alive && p !== player && (player.game!!.isEarly || p.isEnemy(player))
                        && p.findSkill(LENG_XUE_XUN_LIAN) == null && p.cards.isNotEmpty() || continue
                val players = player.game!!.sortedFrom(player.game!!.players.filter { it!!.alive }, p.location)
                for (c in listOf(Black, Red, Blue)) {
                    var sum = 0.0
                    var n = 0.0
                    var currentPercent = 1.0
                    for (i in players.indices) {
                        val inFrontOfWhom = players[(i + 1) % players.size]
                        var m = currentPercent
                        if (player.isPartnerOrSelf(inFrontOfWhom)) m *= 1.2
                        val v = player.calculateMessageCardValue(player, inFrontOfWhom, listOf(c))
                        sum += v * m
                        n += m
                    }
                    currentPercent = 1.0
                    for (i in (players.size - 1) downTo 0) {
                        val inFrontOfWhom = players[(i + 1) % players.size]
                        var m = currentPercent
                        if (player.isPartnerOrSelf(inFrontOfWhom)) m *= 1.2
                        val v = player.calculateMessageCardValue(player, inFrontOfWhom, listOf(c))
                        sum += v * m
                        n += m
                    }
                    if (sum / n / 1.5 > value) {
                        value = sum / n / 1.5
                        target = p
                        color = c
                    }
                }
            }
            target ?: return null
            val secret = (0..2).first { card.secret[it] == color }
            return (value + 10.0) to {
                GameExecutor.post(
                    player.game!!,
                    { card.execute(player.game!!, player, target, secret) },
                    2,
                    TimeUnit.SECONDS
                )
            }
        }
    }
}