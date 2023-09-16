package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.ReceivePhaseSkill
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Fengsheng.end_receive_phase_tos
import com.fengsheng.protos.Role.skill_mi_xin_toc
import com.fengsheng.protos.Role.skill_mi_xin_tos
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 成年韩梅技能【密信】：接收双色情报后，可以将一张含相同颜色的情报从手牌置入传出者的情报区，然后摸两张牌。
 */
class MiXin : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.MI_XIN

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm as? ReceivePhaseSkill ?: return null
        fsm.askWhom === fsm.inFrontOfWhom || return null
        fsm.inFrontOfWhom.findSkill(skillId) != null || return null
        fsm.inFrontOfWhom.getSkillUseCount(skillId) == 0 || return null
        fsm.inFrontOfWhom.cards.isNotEmpty() || return null
        fsm.messageCard.colors.size == 2 || return null
        fsm.sender.alive || return null
        fsm.inFrontOfWhom.addSkillUseCount(skillId)
        return ResolveResult(executeMiXin(fsm, fsm.messageCard.colors), true)
    }

    private data class executeMiXin(val fsm: ReceivePhaseSkill, val colors: List<color>) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in fsm.whoseTurn.game!!.players)
                p!!.notifyReceivePhase(fsm.whoseTurn, fsm.inFrontOfWhom, fsm.messageCard, fsm.inFrontOfWhom)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.inFrontOfWhom) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message is end_receive_phase_tos) {
                if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                    log.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                    player.sendErrorMessage("操作太晚了")
                    return null
                }
                player.incrSeq()
                return ResolveResult(fsm, true)
            }
            if (message !is skill_mi_xin_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val r = fsm.inFrontOfWhom
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            val card = r.findCard(message.cardId)
            if (card == null) {
                log.error("没有这张牌")
                (r as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                return null
            }
            if (!card.colors.any { it in colors }) {
                log.error("选择的情报不含相同颜色")
                (r as? HumanPlayer)?.sendErrorMessage("选择的情报不含相同颜色")
                return null
            }
            r.incrSeq()
            val target = fsm.sender
            log.info("${r}发动了[密信]，将${card}置入${target}的情报区")
            r.deleteCard(card.id)
            target.messageCards.add(card)
            fsm.receiveOrder.addPlayerIfHasThreeBlack(target)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_mi_xin_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.card = card.toPbCard()
                    p.send(builder.build())
                }
            }
            r.draw(2)
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(executeMiXin::class.java)
        }
    }

    companion object {
        fun ai(fsm0: Fsm): Boolean {
            if (fsm0 !is executeMiXin) return false
            val p = fsm0.fsm.inFrontOfWhom
            val card = p.cards.filter {
                !it.isPureBlack() && it.colors.any { c -> c in fsm0.colors }
            }.randomOrNull() ?: return false
            GameExecutor.post(p.game!!, {
                val builder = skill_mi_xin_tos.newBuilder()
                builder.cardId = card.id
                p.game!!.tryContinueResolveProtocol(p, builder.build())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}