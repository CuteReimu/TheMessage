package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.ReceivePhaseSenderSkill
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Fengsheng.end_receive_phase_tos
import com.fengsheng.protos.Role.skill_lian_min_toc
import com.fengsheng.protos.Role.skill_lian_min_tos
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 白菲菲技能【怜悯】：你传出的非黑色情报被接收后，可以从你或接收者的情报区选择一张黑色情报加入你的手牌。
 */
class LianMin : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.LIAN_MIN

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm as? ReceivePhaseSenderSkill
        if (fsm?.whoseTurn?.findSkill(skillId) == null) return null
        if (fsm.messageCard.colors.contains(color.Black)) return null
        if (fsm.whoseTurn.getSkillUseCount(skillId) > 0) return null
        fsm.whoseTurn.addSkillUseCount(skillId)
        return ResolveResult(executeLianMin(fsm), true)
    }

    private data class executeLianMin(val fsm: ReceivePhaseSenderSkill) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in fsm.whoseTurn.game!!.players)
                p!!.notifyReceivePhase(fsm.whoseTurn, fsm.inFrontOfWhom, fsm.messageCard, fsm.whoseTurn, 15)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.whoseTurn) {
                log.error("不是你发技能的时机")
                return null
            }
            if (message is end_receive_phase_tos) {
                if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                    log.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                    return null
                }
                player.incrSeq()
                return ResolveResult(fsm, true)
            }
            if (message !is skill_lian_min_tos) {
                log.error("错误的协议")
                return null
            }
            val r = fsm.whoseTurn
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                return null
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= r.game!!.players.size) {
                log.error("目标错误")
                return null
            }
            val target = r.game!!.players[r.getAbstractLocation(message.targetPlayerId)]
            if (target !== r && target !== fsm.inFrontOfWhom) {
                log.error("只能以自己或者情报接收者为目标")
                return null
            }
            if (!target.alive) {
                log.error("目标已死亡")
                return null
            }
            val card = target.findMessageCard(message.cardId)
            if (card == null) {
                log.error("没有这张卡")
                return null
            }
            if (!card.colors.contains(color.Black)) {
                log.error("你选择的不是黑色情报")
                return null
            }
            r.incrSeq()
            log.info("${r}发动了[怜悯]")
            target.deleteMessageCard(card.id)
            r.cards.add(card)
            log.info("${target}面前的${card}加入了${r}的手牌")
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

        companion object {
            private val log = Logger.getLogger(executeLianMin::class.java)
        }
    }

    companion object {
        fun ai(fsm0: Fsm): Boolean {
            if (fsm0 !is executeLianMin) return false
            val p = fsm0.fsm.whoseTurn
            for (target in arrayOf(p, fsm0.fsm.inFrontOfWhom)) {
                if (target.alive) continue
                val card = target.messageCards.find { it.colors.contains(color.Black) } ?: continue
                GameExecutor.post(p.game!!, {
                    val builder = skill_lian_min_tos.newBuilder()
                    builder.cardId = card.id
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    p.game!!.tryContinueResolveProtocol(p, builder.build())
                }, 2, TimeUnit.SECONDS)
                return true
            }
            return false
        }
    }
}