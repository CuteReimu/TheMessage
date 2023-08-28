package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.OnAddMessageCard
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Fengsheng.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
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

    override val type: card_type
        get() = card_type.Feng_Yun_Bian_Huan

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r === g.jinBiPlayer) {
            log.error("你被禁闭了，不能出牌")
            (r as? HumanPlayer)?.sendErrorMessage("你被禁闭了，不能出牌")
            return false
        }
        if (g.qiangLingTypes.contains(type)) {
            log.error("风云变幻被禁止使用了")
            (r as? HumanPlayer)?.sendErrorMessage("风云变幻被禁止使用了")
            return false
        }
        if (r !== (g.fsm as? MainPhaseIdle)?.player) {
            log.error("风云变幻的使用时机不对")
            (r as? HumanPlayer)?.sendErrorMessage("风云变幻的使用时机不对")
            return false
        }
        return true
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val fsm = g.fsm as MainPhaseIdle
        r.deleteCard(id)
        val players = LinkedList<Player>()
        for (i in r.location until r.location + g.players.size) {
            val player = g.players[i % g.players.size]!!
            if (player.alive) players.add(player)
        }
        val drawCards = arrayListOf(*(r.game!!.deck.draw(players.size)))
        while (players.size > drawCards.size) {
            players.removeLast() // 兼容牌库抽完的情况
        }
        log.info("${r}使用了${this}，翻开了${drawCards.toTypedArray().contentToString()}")
        for (player in r.game!!.players) {
            if (player is HumanPlayer) {
                val builder = use_feng_yun_bian_huan_toc.newBuilder()
                builder.setCard(toPbCard()).playerId = player.getAlternativeLocation(r.location)
                for (c in drawCards) {
                    builder.addShowCards(c.toPbCard())
                }
                player.send(builder.build())
            }
        }
        val resolveFunc = { _: Boolean ->
            executeFengYunBianHuan(this@FengYunBianHuan, drawCards, players, fsm)
        }
        g.resolve(OnUseCard(r, r, null, this, card_type.Feng_Yun_Bian_Huan, r, resolveFunc, fsm))
    }

    private data class executeFengYunBianHuan(
        val card: FengYunBianHuan,
        val drawCards: ArrayList<Card>,
        val players: LinkedList<Player>,
        val mainPhaseIdle: MainPhaseIdle
    ) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = players.firstOrNull()
            if (r == null) {
                mainPhaseIdle.player.game!!.deck.discard(*drawCards.toTypedArray())
                return ResolveResult(
                    OnFinishResolveCard(
                        mainPhaseIdle.player,
                        mainPhaseIdle.player,
                        null,
                        card,
                        card_type.Feng_Yun_Bian_Huan,
                        mainPhaseIdle.player,
                        mainPhaseIdle,
                    ), true
                )
            }
            for (player in r.game!!.players) {
                if (player is HumanPlayer) {
                    val builder = wait_for_feng_yun_bian_huan_choose_card_toc.newBuilder()
                    builder.playerId = player.getAlternativeLocation(r.location)
                    builder.waitingSecond = 15
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
                log.error("现在正在结算风云变幻")
                (player as? HumanPlayer)?.sendErrorMessage("现在正在结算风云变幻")
                return null
            }
            val chooseCard = drawCards.find { c -> c.id == message.cardId }
            if (chooseCard == null) {
                log.error("没有这张牌")
                (player as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                return null
            }
            if (player !== players.first()) {
                log.error("还没轮到你选牌")
                (player as? HumanPlayer)?.sendErrorMessage("还没轮到你选牌")
                return null
            }
            if (message.asMessageCard) {
                val containsSame = player.messageCards.any { c -> c.hasSameColor(chooseCard) }
                if (containsSame) {
                    log.error("已有相同颜色情报，不能作为情报牌")
                    (player as? HumanPlayer)?.sendErrorMessage("已有相同颜色情报，不能作为情报牌")
                    return null
                }
            }
            player.incrSeq()
            players.removeFirst()
            drawCards.removeAt(drawCards.indexOfFirst { c -> c.id == chooseCard.id })
            if (message.asMessageCard) {
                log.info("${player}把${chooseCard}置入情报区")
                player.messageCards.add(chooseCard)
            } else {
                log.info("${player}把${chooseCard}加入手牌")
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
            if (message.asMessageCard)
                return ResolveResult(OnAddMessageCard(mainPhaseIdle.player, this, false), true)
            return ResolveResult(this, true)
        }

        private fun autoChooseCard() {
            val chooseCard = drawCards.first()
            val r = players.first()
            val builder = feng_yun_bian_huan_choose_card_tos.newBuilder()
            builder.cardId = chooseCard.id
            builder.asMessageCard = false
            if (r is HumanPlayer) builder.seq = r.seq
            r.game!!.tryContinueResolveProtocol(r, builder.build())
        }

        companion object {
            private val log = Logger.getLogger(executeFengYunBianHuan::class.java)
        }
    }

    override fun toString(): String {
        return "${cardColorToString(colors)}风云变幻"
    }

    companion object {
        private val log = Logger.getLogger(FengYunBianHuan::class.java)
        fun ai(e: MainPhaseIdle, card: Card): Boolean {
            val player = e.player
            if (player.game!!.qiangLingTypes.contains(card_type.Feng_Yun_Bian_Huan)) return false
            GameExecutor.post(
                player.game!!,
                { card.execute(player.game!!, player) },
                2,
                TimeUnit.SECONDS
            )
            return true
        }
    }
}