package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.ResolveCard
import com.fengsheng.protos.Common.card_type.Feng_Yun_Bian_Huan
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.color.Blue
import com.fengsheng.protos.Common.color.Red
import com.fengsheng.protos.Common.direction
import com.fengsheng.protos.Common.phase.Main_Phase
import com.fengsheng.protos.Common.secret_task.*
import com.fengsheng.protos.Fengsheng.*
import com.fengsheng.skill.cannotPlayCard
import com.google.protobuf.GeneratedMessageV3
import org.apache.logging.log4j.kotlin.logger
import java.util.*
import java.util.concurrent.TimeUnit

class FengYunBianHuan : Card {
    constructor(id: Int, colors: List<color>, direction: direction, lockable: Boolean) :
            super(id, colors, direction, lockable)

    constructor(id: Int, card: Card) : super(id, card)

    /**
     * 仅用于“作为风云变幻使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type = Feng_Yun_Bian_Huan

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r.cannotPlayCard(type)) {
            logger.error("你被禁止使用风云变幻")
            (r as? HumanPlayer)?.sendErrorMessage("你被禁止使用风云变幻")
            return false
        }
        if (r !== (g.fsm as? MainPhaseIdle)?.whoseTurn) {
            logger.error("风云变幻的使用时机不对")
            (r as? HumanPlayer)?.sendErrorMessage("风云变幻的使用时机不对")
            return false
        }
        return true
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val fsm = g.fsm as MainPhaseIdle
        r.deleteCard(id)
        val players = LinkedList<Player>()
        for (i in r.location..<r.location + g.players.size) {
            val player = g.players[i % g.players.size]!!
            if (player.alive) players.add(player)
        }
        val drawCards = arrayListOf(*(r.game!!.deck.draw(players.size)))
        while (players.size > drawCards.size) {
            players.removeLast() // 兼容牌库抽完的情况
        }
        logger.info("${r}使用了${this}，翻开了${drawCards.toTypedArray().contentToString()}")
        for (player in r.game!!.players) {
            if (player is HumanPlayer) {
                val builder = use_feng_yun_bian_huan_toc.newBuilder()
                builder.card = toPbCard()
                builder.playerId = player.getAlternativeLocation(r.location)
                for (c in drawCards) {
                    builder.addShowCards(c.toPbCard())
                }
                player.send(builder.build())
            }
        }
        val resolveFunc = { _: Boolean ->
            executeFengYunBianHuan(this@FengYunBianHuan, drawCards, players, fsm)
        }
        g.resolve(ResolveCard(r, r, null, getOriginCard(), Feng_Yun_Bian_Huan, resolveFunc, fsm))
    }

    private data class executeFengYunBianHuan(
        val card: FengYunBianHuan,
        val drawCards: ArrayList<Card>,
        val players: LinkedList<Player>,
        val mainPhaseIdle: MainPhaseIdle,
        val asMessageCard: Boolean = false
    ) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val p = mainPhaseIdle.whoseTurn
            val r = players.firstOrNull()
            if (r == null) {
                p.game!!.deck.discard(*drawCards.toTypedArray())
                // 向客户端发送notify_phase_toc，客户端关闭风云变幻的弹窗
                for (player in p.game!!.players) {
                    if (player is HumanPlayer) {
                        val builder = notify_phase_toc.newBuilder()
                        builder.currentPlayerId = player.getAlternativeLocation(p.location)
                        builder.currentPhase = Main_Phase
                        player.send(builder.build())
                    }
                }
                if (asMessageCard) p.game!!.addEvent(AddMessageCardEvent(p, false))
                val newFsm = OnFinishResolveCard(p, p, null, card.getOriginCard(), Feng_Yun_Bian_Huan, mainPhaseIdle)
                return ResolveResult(newFsm, true)
            }
            for (player in r.game!!.players) {
                if (player is HumanPlayer) {
                    val builder = wait_for_feng_yun_bian_huan_choose_card_toc.newBuilder()
                    builder.playerId = player.getAlternativeLocation(r.location)
                    builder.waitingSecond = Config.WaitSecond
                    if (player === r) {
                        val seq2: Int = player.seq
                        builder.seq = seq2
                        player.timeout = GameExecutor.post(r.game!!, {
                            if (player.checkSeq(seq2)) {
                                player.incrSeq()
                                autoChooseCard()
                            }
                        }, player.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    player.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(r.game!!, { autoChooseCard() }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (message !is feng_yun_bian_huan_choose_card_tos) {
                logger.error("现在正在结算风云变幻")
                (player as? HumanPlayer)?.sendErrorMessage("现在正在结算风云变幻")
                return null
            }
            val chooseCard = drawCards.find { c -> c.id == message.cardId }
            if (chooseCard == null) {
                logger.error("没有这张牌")
                (player as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                return null
            }
            if (player !== players.first()) {
                logger.error("还没轮到你选牌")
                (player as? HumanPlayer)?.sendErrorMessage("还没轮到你选牌")
                return null
            }
            if (message.asMessageCard) {
                val containsSame = player.messageCards.any { c -> c.hasSameColor(chooseCard) }
                if (containsSame) {
                    logger.error("已有相同颜色情报，不能作为情报牌")
                    (player as? HumanPlayer)?.sendErrorMessage("已有相同颜色情报，不能作为情报牌")
                    return null
                }
            }
            player.incrSeq()
            players.removeFirst()
            drawCards.removeAt(drawCards.indexOfFirst { c -> c.id == chooseCard.id })
            if (message.asMessageCard) {
                logger.info("${player}把${chooseCard}置入情报区")
                player.messageCards.add(chooseCard)
            } else {
                logger.info("${player}把${chooseCard}加入手牌")
                player.cards.add(chooseCard)
            }
            for (p in player.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = feng_yun_bian_huan_choose_card_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(player.location)
                    builder.cardId = message.cardId
                    builder.asMessageCard = message.asMessageCard
                    p.send(builder.build())
                }
            }
            if (!asMessageCard && message.asMessageCard)
                return ResolveResult(copy(asMessageCard = true), true)
            return ResolveResult(this, true)
        }

        private fun autoChooseCard() {
            val r = players.first()
            val builder = feng_yun_bian_huan_choose_card_tos.newBuilder()
            if (r is HumanPlayer) {
                builder.cardId = drawCards.first().id
                builder.asMessageCard = false
                builder.seq = r.seq
            } else {
                fun containsSame(chooseCard: Card) = r.messageCards.any { c -> c.hasSameColor(chooseCard) }
                var card: Card? = null
                when (r.identity) {
                    Red -> {
                        drawCards.filter { !it.isPureBlack() && !containsSame(it) }.run {
                            find { Red in it.colors && !it.isBlack() }
                                ?: find { Red in it.colors }
                                ?: find { !it.isBlack() }
                                ?: firstOrNull()
                        }?.let { card = it }
                    }

                    Blue -> {
                        drawCards.filter { !it.isPureBlack() && !containsSame(it) }.run {
                            find { Blue in it.colors && !it.isBlack() }
                                ?: find { Blue in it.colors }
                                ?: find { !it.isBlack() }
                                ?: firstOrNull()
                        }?.let { card = it }
                    }

                    else -> {
                        when (r.secretTask) {
                            Sweeper -> {
                                if (!r.messageCards.any { it.isBlack() }) {
                                    drawCards.filter { it.isBlack() && !containsSame(it) }.run {
                                        find { it.isPureBlack() } ?: firstOrNull()
                                    }?.let { card = it }
                                }
                            }

                            Killer, Pioneer -> {
                                drawCards.filter { !containsSame(it) }.run {
                                    find { it.isBlack() && !it.isPureBlack() }
                                        ?: find { it.isPureBlack() }
                                        ?: firstOrNull()
                                }?.let { card = it }
                            }

                            else -> {
                                drawCards.filter { !it.isPureBlack() && !containsSame(it) }.run {
                                    find { !it.isBlack() } ?: firstOrNull()
                                }?.let { card = it }
                            }
                        }
                    }
                }
                if (card != null) {
                    builder.cardId = card!!.id
                    builder.asMessageCard = true
                } else {
                    builder.cardId = drawCards.first().id
                    builder.asMessageCard = false
                }
            }
            r.game!!.tryContinueResolveProtocol(r, builder.build())
        }
    }

    override fun toString(): String {
        return "${cardColorToString(colors)}风云变幻"
    }

    companion object {
        fun ai(e: MainPhaseIdle, card: Card): Boolean {
            val player = e.whoseTurn
            !player.cannotPlayCard(Feng_Yun_Bian_Huan) || return false
            GameExecutor.post(
                player.game!!,
                { card.asCard(Feng_Yun_Bian_Huan).execute(player.game!!, player) },
                2,
                TimeUnit.SECONDS
            )
            return true
        }
    }
}