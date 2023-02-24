package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.card.PoYiimport

com.fengsheng.card.PoYi.executePoYiimport com.fengsheng.phase.OnUseCardimport com.fengsheng.phase.SendPhaseIdleimport com.fengsheng.protos.Common.*import com.fengsheng.protos.Fengshengimport

com.fengsheng.protos.Fengsheng.po_yi_show_tocimport com.fengsheng.protos.Fengsheng.use_po_yi_tocimport com.google.protobuf.GeneratedMessageV3import org.apache.log4j.Loggerimport java.util.concurrent.*
class PoYi : Card {
    constructor(id: Int, colors: Array<color>, direction: direction, lockable: Boolean) : super(
        id,
        colors,
        direction,
        lockable
    )

    constructor(id: Int, card: Card?) : super(id, card)

    /**
     * 仅用于“作为破译使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type: card_type
        get() = card_type.Po_Yi

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r === g.jinBiPlayer) {
            log.error("你被禁闭了，不能出牌")
            return false
        }
        if (g.qiangLingTypes.contains(type)) {
            log.error("破译被禁止使用了")
            return false
        }
        if (g.fsm !is SendPhaseIdle || r !== fsm.inFrontOfWhom) {
            log.error("破译的使用时机不对")
            return false
        }
        if (fsm.isMessageCardFaceUp) {
            log.error("破译不能对已翻开的情报使用")
            return false
        }
        return true
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val fsm = g.fsm as SendPhaseIdle
        log.info(r.toString() + "使用了" + this)
        r.deleteCard(id)
        val resolveFunc = Fsm { ResolveResult(executePoYi(this, fsm), true) }
        g.resolve(OnUseCard(r, r, null, this, card_type.Po_Yi, r, resolveFunc))
    }

    @JvmRecord
    private data class executePoYi(val card: PoYi, val sendPhase: SendPhaseIdle) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = sendPhase.inFrontOfWhom
            for (player in r.game.players) {
                if (player is HumanPlayer) {
                    val builder = use_po_yi_toc.newBuilder()
                    builder.setCard(card.toPbCard()).playerId = player.getAlternativeLocation(r.location())
                    builder.setMessageCard(sendPhase.messageCard.toPbCard()).waitingSecond = 15
                    if (player === r) {
                        val seq2: Int = player.seq
                        builder.setSeq(seq2).card = card.toPbCard()
                        player.setTimeout(GameExecutor.Companion.post(r.getGame(), Runnable {
                            if (player.checkSeq(seq2)) {
                                player.incrSeq()
                                showAndDrawCard(false)
                                r.getGame().resolve(sendPhase)
                            }
                        }, player.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS))
                    }
                    player.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.Companion.post(r.getGame(), Runnable {
                    showAndDrawCard(sendPhase.messageCard.getColors().contains(color.Black))
                    r.getGame().resolve(sendPhase)
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (message !is Fengsheng.po_yi_show_tos) {
                log.error("现在正在结算破译")
                return null
            }
            if (player !== sendPhase.inFrontOfWhom) {
                log.error("你不是破译的使用者")
                return null
            }
            if (message.show && !sendPhase.messageCard.getColors().contains(color.Black)) {
                log.error("非黑牌不能翻开")
                return null
            }
            player.incrSeq()
            showAndDrawCard(message.show)
            return ResolveResult(sendPhase, true)
        }

        private fun showAndDrawCard(show: Boolean) {
            val r = sendPhase.inFrontOfWhom
            if (show) {
                log.info(sendPhase.messageCard.toString() + "被翻开了")
                sendPhase.isMessageCardFaceUp = true
                r.draw(1)
            }
            for (player in r.game.players) {
                if (player is HumanPlayer) {
                    val builder = po_yi_show_toc.newBuilder()
                    builder.setPlayerId(player.getAlternativeLocation(r.location())).show = show
                    if (show) builder.messageCard = sendPhase.messageCard.toPbCard()
                    player.send(builder.build())
                }
            }
            r.game.deck.discard(card.originCard)
        }

        companion object {
            private val log = Logger.getLogger(executePoYi::class.java)
        }
    }

    override fun toString(): String {
        return Card.Companion.cardColorToString(colors) + "破译"
    }

    companion object {
        private val log = Logger.getLogger(PoYi::class.java)
        fun ai(e: SendPhaseIdle, card: Card): Boolean {
            val player = e.inFrontOfWhom
            if (player.game.qiangLingTypes.contains(card_type.Po_Yi)) return false
            if (player === e.whoseTurn || e.isMessageCardFaceUp) return false
            if (ThreadLocalRandom.current().nextBoolean()) return false
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