package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.sortCards
import com.fengsheng.card.Card
import com.fengsheng.protos.Fengsheng.end_receive_phase_tos
import com.fengsheng.protos.Role.skill_lian_min_toc
import com.fengsheng.protos.Role.skill_lian_min_tos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 白菲菲技能【怜悯】：你传出的非黑色情报被接收后，可以从你或接收者的情报区选择一张黑色情报加入你的手牌。
 */
class LianMin : TriggeredSkill {
    override val skillId = SkillId.LIAN_MIN

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<ReceiveCardEvent>(this) { event ->
            askWhom === event.sender || return@findEvent false
            !event.messageCard.isBlack() || return@findEvent false
            askWhom.messageCards.any { it.isBlack() } || event.inFrontOfWhom.messageCards.any { it.isBlack() }
        } ?: return null
        return ResolveResult(executeLianMin(g.fsm!!, event), true)
    }

    private data class executeLianMin(val fsm: Fsm, val event: ReceiveCardEvent) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in event.sender.game!!.players)
                p!!.notifyReceivePhase(event.whoseTurn, event.inFrontOfWhom, event.messageCard, event.sender)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== event.sender) {
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
            if (message !is skill_lian_min_tos) {
                logger.error("错误的协议")
                player.sendErrorMessage("错误的协议")
                return null
            }
            val r = event.sender
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= r.game!!.players.size) {
                logger.error("目标错误")
                player.sendErrorMessage("目标错误")
                return null
            }
            val target = r.game!!.players[r.getAbstractLocation(message.targetPlayerId)]
            if (target !== r && target !== event.inFrontOfWhom) {
                logger.error("只能以自己或者情报接收者为目标")
                player.sendErrorMessage("只能以自己或者情报接收者为目标")
                return null
            }
            if (!target.alive) {
                logger.error("目标已死亡")
                player.sendErrorMessage("目标已死亡")
                return null
            }
            val card = target.findMessageCard(message.cardId)
            if (card == null) {
                logger.error("没有这张卡")
                player.sendErrorMessage("没有这张卡")
                return null
            }
            if (!card.isBlack()) {
                logger.error("你选择的不是黑色情报")
                player.sendErrorMessage("你选择的不是黑色情报")
                return null
            }
            r.incrSeq()
            logger.info("${r}发动了[怜悯]，将${target}面前的${card}加入了手牌")
            target.deleteMessageCard(card.id)
            r.cards.add(card)
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_lian_min_toc.newBuilder()
                    builder.cardId = card.id
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    p.send(builder.build())
                }
            }
            return ResolveResult(fsm, true)
        }
    }

    companion object {
        fun ai(fsm0: Fsm): Boolean {
            if (fsm0 !is executeLianMin) return false
            val p = fsm0.event.sender
            var value = 0
            var card: Card? = null
            var targetPlayer: Player? = null
            for (target in listOf(fsm0.event.inFrontOfWhom, p)) {
                target.alive || continue
                val cards = target.messageCards.filter { it.isBlack() }
                cards.isNotEmpty() || continue
                for (c in cards.sortCards(p.identity)) {
                    val v = p.calculateRemoveCardValue(fsm0.event.whoseTurn, target, c)
                    if (v > value) {
                        value = v
                        card = c
                        targetPlayer = target
                    }
                }
            }
            if (card != null && targetPlayer != null) {
                GameExecutor.post(p.game!!, {
                    val builder = skill_lian_min_tos.newBuilder()
                    builder.cardId = card.id
                    builder.targetPlayerId = p.getAlternativeLocation(targetPlayer.location)
                    p.game!!.tryContinueResolveProtocol(p, builder.build())
                }, 3, TimeUnit.SECONDS)
                return true
            }
            return false
        }
    }
}