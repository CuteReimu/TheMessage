package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.bestCard
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.ResolveCard
import com.fengsheng.protos.*
import com.fengsheng.protos.Common.card_type.Feng_Yun_Bian_Huan
import com.fengsheng.protos.Common.card_type.Wei_Bi
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Common.direction
import com.fengsheng.protos.Common.phase.Main_Phase
import com.fengsheng.protos.Common.secret_task.*
import com.fengsheng.protos.Fengsheng.feng_yun_bian_huan_choose_card_tos
import com.fengsheng.skill.ConvertCardSkill
import com.fengsheng.skill.cannotPlayCard
import com.google.protobuf.GeneratedMessage
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
            r.sendErrorMessage("你被禁止使用风云变幻")
            return false
        }
        if (r !== (g.fsm as? MainPhaseIdle)?.whoseTurn) {
            logger.error("风云变幻的使用时机不对")
            r.sendErrorMessage("风云变幻的使用时机不对")
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
        val drawCards = r.game!!.deck.draw(players.size).toMutableList()
        while (players.size > drawCards.size) {
            players.removeLast() // 兼容牌库抽完的情况
        }
        g.turn += g.players.size
        logger.info("${r}使用了$this，翻开了${drawCards.joinToString()}")
        r.game!!.players.send {
            useFengYunBianHuanToc {
                card = toPbCard()
                playerId = it.getAlternativeLocation(r.location)
                drawCards.forEach { showCards.add(it.toPbCard()) }
            }
        }
        val resolveFunc = { _: Boolean ->
            ExecuteFengYunBianHuan(this@FengYunBianHuan, drawCards, players, fsm)
        }
        r.weiBiFailRate = 0
        g.resolve(ResolveCard(r, r, null, getOriginCard(), Feng_Yun_Bian_Huan, resolveFunc, fsm))
    }

    private data class ExecuteFengYunBianHuan(
        val card: FengYunBianHuan,
        val drawCards: MutableList<Card>,
        val players: LinkedList<Player>,
        val mainPhaseIdle: MainPhaseIdle,
        val asMessageCard: Boolean = false
    ) : WaitingFsm {
        override val whoseTurn
            get() = mainPhaseIdle.whoseTurn

        override fun resolve(): ResolveResult? {
            val p = mainPhaseIdle.whoseTurn
            val r = players.firstOrNull()
            if (r == null) {
                p.game!!.deck.discard(drawCards)
                // 向客户端发送notify_phase_toc，客户端关闭风云变幻的弹窗
                p.game!!.players.send {
                    notifyPhaseToc {
                        currentPlayerId = it.getAlternativeLocation(p.location)
                        currentPhase = Main_Phase
                    }
                }
                if (asMessageCard) p.game!!.addEvent(AddMessageCardEvent(p, false))
                val newFsm = OnFinishResolveCard(p, p, null, card.getOriginCard(), Feng_Yun_Bian_Huan, mainPhaseIdle)
                return ResolveResult(newFsm, true)
            }
            r.game!!.players.send { player ->
                waitForFengYunBianHuanChooseCardToc {
                    playerId = player.getAlternativeLocation(r.location)
                    waitingSecond = Config.WaitSecond
                    if (player === r) {
                        val seq2 = player.seq
                        seq = seq2
                        player.timeout = GameExecutor.post(r.game!!, {
                            if (player.checkSeq(seq2)) {
                                player.incrSeq()
                                autoChooseCard()
                            }
                        }, player.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                }
            }
            if (r is RobotPlayer)
                GameExecutor.post(r.game!!, { autoChooseCard() }, 2500, TimeUnit.MILLISECONDS)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (message !is feng_yun_bian_huan_choose_card_tos) {
                logger.error("现在正在结算风云变幻")
                player.sendErrorMessage("现在正在结算风云变幻")
                return null
            }
            val chooseCard = drawCards.find { c -> c.id == message.cardId }
            if (chooseCard == null) {
                logger.error("没有这张牌")
                player.sendErrorMessage("没有这张牌")
                return null
            }
            if (player !== players.first()) {
                logger.error("还没轮到你选牌")
                player.sendErrorMessage("还没轮到你选牌")
                return null
            }
            if (message.asMessageCard) {
                val containsSame = player.messageCards.any { c -> c.hasSameColor(chooseCard) }
                if (containsSame) {
                    logger.error("已有相同颜色情报，不能作为情报牌")
                    player.sendErrorMessage("已有相同颜色情报，不能作为情报牌")
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
            player.game!!.players.send {
                fengYunBianHuanChooseCardToc {
                    playerId = it.getAlternativeLocation(player.location)
                    cardId = message.cardId
                    asMessageCard = message.asMessageCard
                }
            }
            if (!asMessageCard && message.asMessageCard)
                return ResolveResult(copy(asMessageCard = true), true)
            return ResolveResult(this, true)
        }

        private fun autoChooseCard() {
            val r = players.first()
            if (r is HumanPlayer) {
                r.game!!.tryContinueResolveProtocol(r, fengYunBianHuanChooseCardTos {
                    cardId = drawCards.first().id
                    asMessageCard = false
                    seq = r.seq
                })
            } else {
                var value = 0
                var card: Card? = null
                for (c in drawCards) {
                    !r.messageCards.any { it.hasSameColor(c) } || continue
                    val result = r.calculateMessageCardValue(mainPhaseIdle.whoseTurn, r, c)
                    if (result > value) {
                        value = result
                        card = c
                    }
                }
                r.game!!.tryContinueResolveProtocol(r, fengYunBianHuanChooseCardTos {
                    if (card != null) {
                        cardId = card.id
                        asMessageCard = true
                    } else {
                        if (r === mainPhaseIdle.whoseTurn) {
                            cardId = drawCards.filter { it.type == Wei_Bi }
                                .ifEmpty { drawCards }.bestCard(r.identity).id
                            asMessageCard = false
                        } else {
                            cardId = drawCards.bestCard(r.identity).id
                            asMessageCard = false
                        }
                    }
                })
            }
        }
    }

    override fun toString(): String {
        return "${cardColorToString(colors)}风云变幻"
    }

    companion object {
        fun ai(e: MainPhaseIdle, card: Card, convertCardSkill: ConvertCardSkill?): Boolean {
            val player = e.whoseTurn
            !player.cannotPlayCard(Feng_Yun_Bian_Huan) || return false
            when {
                player.identity == Black && player.secretTask == Sweeper -> return false
                player.identity == Black && player.secretTask in listOf(Collector, Mutator) ->
                    if (player.messageCards.any { !it.isPureBlack() }) return false

                player.identity != Black -> if (player.game!!.players.all {
                        !it!!.alive || it.identity != player.identity ||
                            it.messageCards.any { c -> player.identity in c.colors }
                    }) return false
            }
            GameExecutor.post(player.game!!, {
                convertCardSkill?.onConvert(player)
                card.asCard(Feng_Yun_Bian_Huan).execute(player.game!!, player)
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}
