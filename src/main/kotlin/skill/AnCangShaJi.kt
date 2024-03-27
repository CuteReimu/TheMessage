package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.bestCardOrNull
import com.fengsheng.card.Card
import com.fengsheng.protos.Common.color.Blue
import com.fengsheng.protos.Fengsheng.end_receive_phase_tos
import com.fengsheng.protos.Role.skill_an_cang_sha_ji_tos
import com.fengsheng.protos.skillAnCangShaJiToc
import com.fengsheng.protos.skillAnCangShaJiTos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * CP韩梅技能【暗藏杀机】：你接收蓝色情报或你传出的蓝色情报被接收后，可以选择一项：
 * 1. 将一张纯黑色手牌置入其情报区。
 * 2. 你抽取其一张手牌。
 */
class AnCangShaJi : TriggeredSkill {
    override val skillId = SkillId.AN_CANG_SHA_JI

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        var target = askWhom // 暂时赋个初值，下面会改的
        val event = g.findEvent<ReceiveCardEvent>(this) { event ->
            target = when {
                askWhom === event.sender -> event.inFrontOfWhom
                askWhom === event.inFrontOfWhom -> event.sender
                else -> return@findEvent false
            }
            event.sender.alive && event.inFrontOfWhom.alive || return@findEvent false
            Blue in event.messageCard.colors || return@findEvent false
            askWhom.cards.isNotEmpty() || target.cards.isNotEmpty()
        } ?: return null
        return ResolveResult(ExecuteAnCangShaJi(g.fsm!!, event, askWhom, target), true)
    }

    private data class ExecuteAnCangShaJi(
        val fsm: Fsm,
        val event: ReceiveCardEvent,
        val r: Player,
        val target: Player
    ) : WaitingFsm {
        override val whoseTurn: Player
            get() = fsm.whoseTurn

        override fun resolve(): ResolveResult? {
            for (p in r.game!!.players)
                p!!.notifyReceivePhase(event.whoseTurn, event.inFrontOfWhom, event.messageCard, r)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== r) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message is end_receive_phase_tos) {
                if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                    logger.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                    player.sendErrorMessage("操作太晚了")
                    return null
                }
                player.incrSeq()
                return ResolveResult(fsm, true)
            }
            if (message !is skill_an_cang_sha_ji_tos) {
                logger.error("错误的协议")
                player.sendErrorMessage("错误的协议")
                return null
            }
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            var card: Card? = null
            var handCard: Card? = null
            if (message.cardId != 0) {
                card = r.findCard(message.cardId)
                if (card == null) {
                    logger.error("没有这张牌")
                    player.sendErrorMessage("没有这张牌")
                    return null
                }
                if (!card.isPureBlack()) {
                    logger.error("你选择的不是纯黑色牌")
                    player.sendErrorMessage("你选择的不是纯黑色牌")
                    return null
                }
            } else {
                handCard = target.cards.randomOrNull()
                if (handCard == null) {
                    logger.error("${target}没有手牌")
                    player.sendErrorMessage("对方没有手牌")
                    return null
                }
            }
            r.incrSeq()
            if (card == null) {
                logger.info("${r}发动了[暗藏杀机]，抽取了${target}一张$handCard")
                target.deleteCard(handCard!!.id)
                r.cards.add(handCard)
                r.game!!.addEvent(GiveCardEvent(event.whoseTurn, target, r))
            } else {
                logger.info("${r}发动了[暗藏杀机]，将${card}置入${target}的情报区")
                r.deleteCard(card.id)
                target.messageCards.add(card)
                r.game!!.addEvent(AddMessageCardEvent(event.whoseTurn))
            }
            r.game!!.players.send { p ->
                skillAnCangShaJiToc {
                    playerId = p.getAlternativeLocation(r.location)
                    card?.let { this.card = it.toPbCard() }
                    targetPlayerId = p.getAlternativeLocation(target.location)
                    if (p === r || p === target) handCard?.let { this.handCard = it.toPbCard() }
                }
            }
            return ResolveResult(fsm, true)
        }
    }

    companion object {
        fun ai(fsm0: Fsm): Boolean {
            if (fsm0 !is ExecuteAnCangShaJi) return false
            val p = fsm0.r
            val target = fsm0.target
            var card = p.cards.filter { it.isPureBlack() }.bestCardOrNull(p.identity, true)
            if (card != null) {
                val v = p.calculateMessageCardValue(fsm0.event.whoseTurn, target, card)
                if (v <= 0) card = null
            }
            if (card != null || p !== target && target.cards.isNotEmpty()) {
                GameExecutor.post(p.game!!, {
                    p.game!!.tryContinueResolveProtocol(p, skillAnCangShaJiTos { card?.let { cardId = it.id } })
                }, 3, TimeUnit.SECONDS)
                return true
            }
            return false
        }
    }
}
