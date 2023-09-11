package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.OnSendCard
import com.fengsheng.phase.OnUseCard
import com.fengsheng.phase.SendPhaseStart
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Fengsheng.*
import com.fengsheng.protos.Role
import com.fengsheng.skill.LengXueXunLian
import com.fengsheng.skill.SkillId
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
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

    override val type = card_type.Mi_Ling

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
            log.error("密令被禁止使用了")
            (r as? HumanPlayer)?.sendErrorMessage("密令被禁止使用了")
            return false
        }
        if (r !== (g.fsm as? SendPhaseStart)?.player) {
            log.error("密令的使用时机不对")
            (r as? HumanPlayer)?.sendErrorMessage("密令的使用时机不对")
            return false
        }
        val target = args[0] as Player
        if (r === target) {
            log.error("密令不能对自己使用")
            (r as? HumanPlayer)?.sendErrorMessage("密令不能对自己使用")
            return false
        }
        if (!target.alive) {
            log.error("目标已死亡")
            (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
            return false
        }
        if (target.cards.isEmpty()) {
            log.error("目标没有手牌")
            (r as? HumanPlayer)?.sendErrorMessage("目标没有手牌")
            return false
        }
        return true
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val fsm = g.fsm as SendPhaseStart
        val target = args[0] as Player
        val secret = args[1] as Int
        log.info("${r}对${target}使用了$this")
        r.deleteCard(id)
        val color = this.secret[secret]
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
        g.resolve(OnUseCard(r, r, target, this, card_type.Mi_Ling, resolveFunc, fsm))
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
                        builder.cardId = target.cards.first().id
                        builder.seq = seq2
                        r.game!!.tryContinueResolveProtocol(r, builder.build())
                    }
                }, r.getWaitSeconds(timeout + 2).toLong(), TimeUnit.SECONDS)
            } else {
                GameExecutor.post(r.game!!, {
                    val builder = mi_ling_choose_card_tos.newBuilder()
                    builder.cardId = target.cards.first().id
                    r.game!!.tryContinueResolveProtocol(r, builder.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (message !is mi_ling_choose_card_tos) {
                log.error("现在正在结算密令")
                (player as? HumanPlayer)?.sendErrorMessage("现在正在结算密令")
                return null
            }
            if (player !== this.player) {
                log.error("没有轮到你操作")
                (player as? HumanPlayer)?.sendErrorMessage("没有轮到你操作")
                return null
            }
            val card = target.findCard(message.cardId)
            if (card == null) {
                log.error("没有这张牌")
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
            val card = messageCard ?: target.cards.find { this.card.secret[secret] in it.colors }!!
            val messageTarget = when (card.direction) {
                direction.Left -> target.getNextLeftAlivePlayer()
                direction.Right -> target.getNextRightAlivePlayer()
                else -> target.game!!.players.filter { target !== it && it!!.alive }.random()!!
            }
            if (target is HumanPlayer) {
                val seq2 = target.seq
                target.timeout = GameExecutor.post(target.game!!, {
                    if (target.checkSeq(seq2)) {
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
                    val skill = target.findSkill(SkillId.LENG_XUE_XUN_LIAN) as? LengXueXunLian
                    if (skill != null) {
                        skill.executeProtocol(
                            target.game!!,
                            target,
                            Role.skill_leng_xue_xun_lian_a_tos.getDefaultInstance()
                        )
                    } else {
                        val builder = send_message_card_tos.newBuilder()
                        builder.cardId = card.id
                        builder.targetPlayerId = target.getAlternativeLocation(messageTarget.location)
                        builder.cardDir = card.direction
                        target.game!!.tryContinueResolveProtocol(target, builder.build())
                    }
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            val pb = message as? send_message_card_tos
            if (pb == null) {
                log.error("现在正在结算密令")
                (player as? HumanPlayer)?.sendErrorMessage("现在正在结算密令")
                return null
            }
            if (player !== target) {
                log.error("没有轮到你传情报")
                (player as? HumanPlayer)?.sendErrorMessage("没有轮到你传情报")
                return null
            }
            val messageCard =
                if (this.messageCard != null) {
                    if (pb.cardId != this.messageCard.id) {
                        log.error("你没有传递指定情报")
                        (player as? HumanPlayer)?.sendErrorMessage("你没有传递指定情报")
                        return null
                    }
                    this.messageCard
                } else {
                    val c = target.findCard(pb.cardId)
                    if (c == null) {
                        log.error("没有这张牌")
                        (player as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                        return null
                    }
                    if (this.card.secret[secret] !in c.colors) {
                        log.error("不是指定颜色的情报")
                        (player as? HumanPlayer)?.sendErrorMessage("不是指定颜色的情报")
                        return null
                    }
                    c
                }
            if (pb.targetPlayerId <= 0 || pb.targetPlayerId >= target.game!!.players.size) {
                log.error("目标错误: ${pb.targetPlayerId}")
                (player as? HumanPlayer)?.sendErrorMessage("目标错误: ${pb.targetPlayerId}")
                return null
            }
            if (target.findSkill(SkillId.LIAN_LUO) == null && pb.cardDir != messageCard.direction) {
                log.error("方向错误: ${pb.cardDir}")
                (player as? HumanPlayer)?.sendErrorMessage("方向错误: ${pb.cardDir}")
                return null
            }
            var targetLocation = when (pb.cardDir) {
                direction.Left -> target.getNextLeftAlivePlayer().location
                direction.Right -> target.getNextRightAlivePlayer().location
                else -> 0
            }
            if (pb.cardDir != direction.Up && pb.targetPlayerId != target.getAlternativeLocation(targetLocation)) {
                log.error("不能传给那个人: ${pb.targetPlayerId}")
                (player as? HumanPlayer)?.sendErrorMessage("不能传给那个人: ${pb.targetPlayerId}")
                return null
            }
            if (messageCard.canLock()) {
                if (pb.lockPlayerIdCount > 1) {
                    log.error("最多锁定一个目标")
                    (player as? HumanPlayer)?.sendErrorMessage("最多锁定一个目标")
                    return null
                } else if (pb.lockPlayerIdCount == 1) {
                    if (pb.getLockPlayerId(0) < 0 || pb.getLockPlayerId(0) >= target.game!!.players.size) {
                        log.error("锁定目标错误: ${pb.getLockPlayerId(0)}")
                        (player as? HumanPlayer)?.sendErrorMessage("锁定目标错误: ${pb.getLockPlayerId(0)}")
                        return null
                    } else if (pb.getLockPlayerId(0) == 0) {
                        log.error("不能锁定自己")
                        (player as? HumanPlayer)?.sendErrorMessage("不能锁定自己")
                        return null
                    }
                }
            } else {
                if (pb.lockPlayerIdCount > 0) {
                    log.error("这张情报没有锁定标记")
                    (player as? HumanPlayer)?.sendErrorMessage("这张情报没有锁定标记")
                    return null
                }
            }
            targetLocation = target.getAbstractLocation(pb.targetPlayerId)
            if (!target.game!!.players[targetLocation]!!.alive) {
                log.error("目标已死亡")
                (player as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                return null
            }
            val lockPlayers = ArrayList<Player>()
            for (lockPlayerId in pb.lockPlayerIdList) {
                val lockPlayer = target.game!!.players[target.getAbstractLocation(lockPlayerId)]!!
                if (!lockPlayer.alive) {
                    log.error("锁定目标已死亡：$lockPlayer")
                    (player as? HumanPlayer)?.sendErrorMessage("锁定目标已死亡：$lockPlayer")
                    return null
                }
                lockPlayers.add(lockPlayer)
            }
            player.incrSeq()
            val newFsm = OnSendCard(
                sendPhase.player, target, messageCard, pb.cardDir, target.game!!.players[targetLocation]!!,
                lockPlayers.toTypedArray()
            )
            return ResolveResult(
                OnFinishResolveCard(sendPhase.player, target, card, card_type.Mi_Ling, newFsm) {},
                true
            )
        }

        companion object {
            private val log = Logger.getLogger(executeMiLing::class.java)
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
        private val log = Logger.getLogger(MiLing::class.java)
        fun ai(e: SendPhaseStart, card: Card): Boolean {
            val player = e.player
            if (player === player.game!!.jinBiPlayer) return false
            if (player.game!!.qiangLingTypes.contains(card_type.Mi_Ling)) return false
            if (player.location in player.game!!.diaoHuLiShanPlayers) return false
            val target = player.game!!.players.filter {
                it !== player && it!!.alive && it.cards.isNotEmpty()
            }.randomOrNull() ?: return false
            GameExecutor.post(
                player.game!!,
                { card.execute(player.game!!, player, target, (0..2).random()) },
                2,
                TimeUnit.SECONDS
            )
            return true
        }
    }
}