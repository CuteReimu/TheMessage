package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Fengsheng.end_receive_phase_tos
import com.fengsheng.protos.Role.skill_qi_huo_ke_ju_toc
import com.fengsheng.protos.Role.skill_qi_huo_ke_ju_tos
import com.google.protobuf.GeneratedMessageV3
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 毛不拔技能【奇货可居】：你接收双色情报后，可以从你的情报区选择一张情报加入手牌。
 */
class QiHuoKeJu : TriggeredSkill {
    override val skillId = SkillId.QI_HUO_KE_JU

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<ReceiveCardEvent>(this) { event ->
            askWhom === event.inFrontOfWhom || return@findEvent false
            event.messageCard.colors.size == 2
        } ?: return null
        return ResolveResult(executeQiHuoKeJu(g.fsm!!, event), true)
    }

    private data class executeQiHuoKeJu(val fsm: Fsm, val event: ReceiveCardEvent) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in event.whoseTurn.game!!.players)
                p!!.notifyReceivePhase(event.whoseTurn, event.inFrontOfWhom, event.messageCard, event.inFrontOfWhom)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== event.inFrontOfWhom) {
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
            if (message !is skill_qi_huo_ke_ju_tos) {
                logger.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val r = event.inFrontOfWhom
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            val card = r.findMessageCard(message.cardId)
            if (card == null) {
                logger.error("没有这张卡")
                (player as? HumanPlayer)?.sendErrorMessage("没有这张卡")
                return null
            }
            r.incrSeq()
            logger.info("${r}发动了[奇货可居]")
            r.deleteMessageCard(card.id)
            r.cards.add(card)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_qi_huo_ke_ju_toc.newBuilder()
                    builder.cardId = card.id
                    builder.playerId = p.getAlternativeLocation(r.location)
                    p.send(builder.build())
                }
            }
            return ResolveResult(fsm, true)
        }

        companion object {
        }
    }

    companion object {
        fun ai(fsm0: Fsm): Boolean {
            if (fsm0 !is executeQiHuoKeJu) return false
            val p = fsm0.event.inFrontOfWhom
            val card = p.messageCards.find { it.colors.contains(color.Black) } ?: return false
            GameExecutor.post(p.game!!, {
                val builder = skill_qi_huo_ke_ju_tos.newBuilder()
                builder.cardId = card.id
                p.game!!.tryContinueResolveProtocol(p, builder.build())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}