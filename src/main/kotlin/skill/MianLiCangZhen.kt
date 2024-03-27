package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.sortCards
import com.fengsheng.card.Card
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Fengsheng.end_receive_phase_tos
import com.fengsheng.protos.Role.skill_mian_li_cang_zhen_tos
import com.fengsheng.protos.skillMianLiCangZhenToc
import com.fengsheng.protos.skillMianLiCangZhenTos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 邵秀技能【绵里藏针】：你传出的情报被接收后，可以将一张黑色手牌置入接收者的情报区，然后摸一张牌。
 */
class MianLiCangZhen : TriggeredSkill {
    override val skillId = SkillId.MIAN_LI_CANG_ZHEN

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<ReceiveCardEvent>(this) { event ->
            askWhom === event.sender || return@findEvent false
            askWhom.cards.isNotEmpty()
        } ?: return null
        return ResolveResult(ExecuteMianLiCangZhen(g.fsm!!, event), true)
    }

    private data class ExecuteMianLiCangZhen(val fsm: Fsm, val event: ReceiveCardEvent) : WaitingFsm {
        override val whoseTurn: Player
            get() = fsm.whoseTurn

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
            if (message !is skill_mian_li_cang_zhen_tos) {
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
            val card = r.findCard(message.cardId)
            if (card == null) {
                logger.error("没有这张卡")
                player.sendErrorMessage("没有这张卡")
                return null
            }
            if (!card.colors.contains(color.Black)) {
                logger.error("你选择的不是黑色手牌")
                player.sendErrorMessage("你选择的不是黑色手牌")
                return null
            }
            val target = event.inFrontOfWhom
            if (!target.alive) {
                logger.error("目标已死亡")
                player.sendErrorMessage("目标已死亡")
                return null
            }
            r.incrSeq()
            logger.info("${r}发动了[绵里藏针]，将${card}置入${target}的情报区")
            r.deleteCard(card.id)
            target.messageCards.add(card)
            r.game!!.players.send {
                skillMianLiCangZhenToc {
                    this.card = card.toPbCard()
                    playerId = it.getAlternativeLocation(r.location)
                    targetPlayerId = it.getAlternativeLocation(target.location)
                }
            }
            r.draw(1)
            r.game!!.addEvent(AddMessageCardEvent(event.whoseTurn))
            return ResolveResult(fsm, true)
        }
    }

    companion object {
        fun ai(fsm: Fsm): Boolean {
            if (fsm !is ExecuteMianLiCangZhen) return false
            val p = fsm.event.sender
            val target = fsm.event.inFrontOfWhom
            if (!target.alive) return false
            var value = -1
            var card: Card? = null
            for (c in p.cards.sortCards(p.identity, true)) {
                c.isBlack() || continue
                val v = p.calculateMessageCardValue(fsm.event.whoseTurn, target, c)
                if (v > value) {
                    value = v
                    card = c
                }
            }
            card ?: return false
            GameExecutor.post(p.game!!, {
                p.game!!.tryContinueResolveProtocol(p, skillMianLiCangZhenTos { cardId = card.id })
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}
