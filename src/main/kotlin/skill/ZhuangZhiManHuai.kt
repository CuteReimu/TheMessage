package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.sortCards
import com.fengsheng.card.Card
import com.fengsheng.protos.Common.color.Red
import com.fengsheng.protos.Fengsheng.end_receive_phase_tos
import com.fengsheng.protos.Role.skill_zhuang_zhi_man_huai_toc
import com.fengsheng.protos.Role.skill_zhuang_zhi_man_huai_tos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * CP小九技能【壮志满怀】：你接收红色情报或你传出的红色情报被接收后，可以选择一项：
 * 1. 拿取一张你或其的黑色情报到手中。
 * 2. 双方各摸一张牌。
 */
class ZhuangZhiManHuai : TriggeredSkill {
    override val skillId = SkillId.ZHUANG_ZHI_MAN_HUAI

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<ReceiveCardEvent>(this) { event ->
            askWhom === event.sender || askWhom === event.inFrontOfWhom || return@findEvent false
            Red in event.messageCard.colors
        } ?: return null
        return ResolveResult(executeZhuangZhiManHuai(g.fsm!!, event, askWhom), true)
    }

    private data class executeZhuangZhiManHuai(val fsm: Fsm, val event: ReceiveCardEvent, val r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in r.game!!.players)
                p!!.notifyReceivePhase(event.whoseTurn, event.inFrontOfWhom, event.messageCard, r)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== r) {
                logger.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
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
            if (message !is skill_zhuang_zhi_man_huai_tos) {
                logger.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            var target: Player? = null
            var card: Card? = null
            if (message.cardId != 0) {
                if (event.sender.alive)
                    card = event.sender.findMessageCard(message.cardId)?.also { target = event.sender }
                if (card == null && event.inFrontOfWhom.alive)
                    card = event.inFrontOfWhom.findMessageCard(message.cardId)?.also { target = event.inFrontOfWhom }
                if (card == null) {
                    logger.error("没有这张情报")
                    (player as? HumanPlayer)?.sendErrorMessage("没有这张情报")
                    return null
                }
                if (!card.isBlack()) {
                    logger.error("你选择的不是黑色情报")
                    (player as? HumanPlayer)?.sendErrorMessage("你选择的不是黑色情报")
                    return null
                }
            }
            r.incrSeq()
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_zhuang_zhi_man_huai_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    card?.let { builder.card = it.toPbCard() }
                    target?.let { builder.targetPlayerId = p.getAlternativeLocation(it.location) }
                    p.send(builder.build())
                }
            }
            if (card == null) {
                logger.info("${r}发动了[壮志满怀]，选择了双方各摸一张牌")
                if (event.sender === event.inFrontOfWhom)
                    event.sender.draw(2)
                else
                    player.game!!.sortedFrom(listOf(event.sender, event.inFrontOfWhom), event.whoseTurn.location)
                        .forEach { it.draw(1) }
            } else {
                logger.info("${r}发动了[壮志满怀]，将${target}面前的${card}加入了手牌")
                target!!.deleteMessageCard(card.id)
                r.cards.add(card)
            }
            return ResolveResult(fsm, true)
        }
    }

    companion object {
        fun ai(fsm0: Fsm): Boolean {
            if (fsm0 !is executeZhuangZhiManHuai) return false
            val p = fsm0.r
            var value = 0
            var card: Card? = null
            for (target in listOf(fsm0.event.inFrontOfWhom, fsm0.event.sender)) {
                target.alive || continue
                val cards = target.messageCards.filter { it.isBlack() }
                cards.isNotEmpty() || continue
                for (c in cards.sortCards(p.identity)) {
                    val v = p.calculateRemoveCardValue(fsm0.event.whoseTurn, target, c)
                    if (v > value) {
                        value = v
                        card = c
                    }
                }
            }
            GameExecutor.post(p.game!!, {
                val builder = skill_zhuang_zhi_man_huai_tos.newBuilder()
                card?.let { builder.cardId = it.id }
                p.game!!.tryContinueResolveProtocol(p, builder.build())
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}