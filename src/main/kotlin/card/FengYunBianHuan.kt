package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.card.FengYunBianHuanimport

com.fengsheng.card.FengYunBianHuan.executeFengYunBianHuanimport com.fengsheng.phase.MainPhaseIdleimport com.fengsheng.phase.OnUseCardimport com.fengsheng.protos.Common.*import com.fengsheng.protos.Fengshengimport

com.fengsheng.protos.Fengsheng.use_feng_yun_bian_huan_tocimport com.fengsheng.protos.Fengsheng.wait_for_feng_yun_bian_huan_choose_card_tocimport com.google.protobuf.GeneratedMessageV3import org.apache.log4j.Loggerimport java.util.*import java.util.concurrent.*

class FengYunBianHuan : Card {
    constructor(id: Int, colors: Array<color>, direction: direction, lockable: Boolean) : super(
        id,
        colors,
        direction,
        lockable
    )

    constructor(id: Int, card: Card?) : super(id, card)

    /**
     * 仅用于“作为风云变幻使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type: card_type
        get() = card_type.Feng_Yun_Bian_Huan

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r === g.jinBiPlayer) {
            log.error("你被禁闭了，不能出牌")
            return false
        }
        if (g.qiangLingTypes.contains(type)) {
            log.error("风云变幻被禁止使用了")
            return false
        }
        if (g.fsm !is MainPhaseIdle || r !== fsm.player) {
            log.error("风云变幻的使用时机不对")
            return false
        }
        return true
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val fsm = g.fsm as MainPhaseIdle
        r.deleteCard(id)
        val players: Deque<Player> = ArrayDeque()
        for (player in r.game.players) {
            if (player.isAlive) players.add(player)
        }
        val drawCards: MutableMap<Int, Card> = HashMap()
        for (c in r.game.deck.draw(players.size)) {
            drawCards[c.getId()] = c
        }
        while (players.size > drawCards.size) {
            players.removeLast() // 兼容牌库抽完的情况
        }
        log.info(r.toString() + "使用了" + this + "，翻开了" + Arrays.toString(drawCards.values.toTypedArray()))
        for (player in r.game.players) {
            if (player is HumanPlayer) {
                val builder = use_feng_yun_bian_huan_toc.newBuilder()
                builder.setCard(toPbCard()).playerId = player.getAlternativeLocation(r.location())
                for (c in drawCards.values) {
                    builder.addShowCards(c.toPbCard())
                }
                player.send(builder.build())
            }
        }
        val resolveFunc = Fsm { ResolveResult(executeFengYunBianHuan(this, drawCards, players, fsm), true) }
        g.resolve(OnUseCard(r, r, null, this, card_type.Feng_Yun_Bian_Huan, r, resolveFunc))
    }

    private class executeFengYunBianHuan(
        val card: FengYunBianHuan,
        drawCards: MutableMap<Int, Card>,
        players: Queue<Player>,
        mainPhaseIdle: MainPhaseIdle
    ) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = players.peek()
            if (r == null) {
                mainPhaseIdle.player.game.deck.discard(card)
                return ResolveResult(mainPhaseIdle, true)
            }
            for (player in r.game.players) {
                if (player is HumanPlayer) {
                    val builder = wait_for_feng_yun_bian_huan_choose_card_toc.newBuilder()
                    builder.playerId = player.getAlternativeLocation(r.location())
                    builder.waitingSecond = 15
                    if (player === r) {
                        val seq2: Int = player.seq
                        builder.seq = seq2
                        player.setTimeout(GameExecutor.Companion.post(r.getGame(), Runnable {
                            if (player.checkSeq(seq2)) {
                                player.incrSeq()
                                autoChooseCard()
                            }
                        }, player.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS))
                    }
                    player.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.Companion.post(r.getGame(), Runnable { autoChooseCard() }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (message !is Fengsheng.feng_yun_bian_huan_choose_card_tos) {
                log.error("现在正在结算风云变幻")
                return null
            }
            val chooseCard = drawCards[message.cardId]
            if (chooseCard == null) {
                log.error("没有这张牌")
                return null
            }
            assert(!players.isEmpty())
            if (player !== players.peek()) {
                log.error("还没轮到你选牌")
                return null
            }
            if (message.asMessageCard) {
                var containsSame = false
                for (c in player.messageCards.values) {
                    if (c.hasSameColor(chooseCard)) {
                        containsSame = true
                        break
                    }
                }
                if (containsSame) {
                    log.error("已有相同颜色情报，不能作为情报牌")
                    return null
                }
            }
            player.incrSeq()
            players.poll()
            drawCards.remove(chooseCard.getId())
            if (message.asMessageCard) {
                player.addMessageCard(chooseCard)
            } else {
                player.addCard(chooseCard)
            }
            return ResolveResult(this, true)
        }

        private fun autoChooseCard() {
            assert(!drawCards.isEmpty())
            assert(!players.isEmpty())
            var chooseCard: Card? = null
            for (c in drawCards.values) {
                chooseCard = c
                break
            }
            val r = players.peek()
            val builder = Fengsheng.feng_yun_bian_huan_choose_card_tos.newBuilder()
            builder.cardId = chooseCard.getId()
            builder.asMessageCard = false
            if (r is HumanPlayer) builder.seq = r.seq
            r.game.tryContinueResolveProtocol(r, builder.build())
        }

        val drawCards: MutableMap<Int, Card>
        val players: Queue<Player>
        val mainPhaseIdle: MainPhaseIdle

        init {
            this.sendPhase = sendPhase
            this.r = r
            this.target = target
            card = card
            this.wantType = wantType
            this.r = r
            this.target = target
            card = card
            this.player = player
            card = card
            card = card
            this.drawCards = drawCards
            this.players = players
            this.mainPhaseIdle = mainPhaseIdle
        }

        companion object {
            private val log = Logger.getLogger(executeFengYunBianHuan::class.java)
        }
    }

    override fun toString(): String {
        return Card.Companion.cardColorToString(colors) + "风云变幻"
    }

    companion object {
        private val log = Logger.getLogger(FengYunBianHuan::class.java)
        fun ai(e: MainPhaseIdle, card: Card): Boolean {
            val player = e.player
            if (player.game.qiangLingTypes.contains(card_type.Feng_Yun_Bian_Huan)) return false
            GameExecutor.Companion.post(
                player.game,
                Runnable { card.execute(player.game, player) },
                2,
                TimeUnit.SECONDS
            )
            return true
        }
    }
}