package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.ReceivePhaseReceiverSkill
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Fengsheng
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 程小蝶技能【惊梦】：你接收黑色情报后，可以查看一名角色的手牌。
 */
class JingMeng : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.JING_MENG

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm as? ReceivePhaseReceiverSkill
        if (fsm?.inFrontOfWhom?.findSkill(skillId) == null) return null
        if (fsm.inFrontOfWhom.getSkillUseCount(skillId) > 0) return null
        if (!fsm.messageCard.colors.contains(color.Black)) return null
        fsm.inFrontOfWhom.addSkillUseCount(skillId)
        return ResolveResult(executeJingMengA(fsm), true)
    }

    private data class executeJingMengA(val fsm: ReceivePhaseReceiverSkill) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in fsm.whoseTurn.game!!.players)
                p!!.notifyReceivePhase(fsm.whoseTurn, fsm.inFrontOfWhom, fsm.messageCard, fsm.inFrontOfWhom, 15)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.inFrontOfWhom) {
                log.error("不是你发技能的时机")
                return null
            }
            if (message is Fengsheng.end_receive_phase_tos) {
                if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                    log.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                    return null
                }
                player.incrSeq()
                return ResolveResult(fsm, true)
            }
            if (message !is skill_jing_meng_a_tos) {
                log.error("错误的协议")
                return null
            }
            val r = fsm.inFrontOfWhom
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                return null
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                log.error("目标错误：${message.targetPlayerId}")
                return null
            }
            if (message.targetPlayerId == 0) {
                log.error("不能以自己为目标")
                return null
            }
            val target = g.players[r.getAbstractLocation(message.targetPlayerId)]!!
            if (!target.alive) {
                log.error("目标已死亡")
                return null
            }
            if (target.cards.isEmpty()) {
                log.error("目标没有手牌")
                return null
            }
            r.incrSeq()
            log.info("${r}发动了[惊梦]，查看了${target}的手牌")
            return ResolveResult(executeJingMengB(fsm, target), true)
        }

        companion object {
            private val log = Logger.getLogger(executeJingMengA::class.java)
        }
    }

    private data class executeJingMengB(val fsm: ReceivePhaseReceiverSkill, val target: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = fsm.inFrontOfWhom
            val g = r.game!!
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jing_meng_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.waitingSecond = 15
                    if (p === r) {
                        for (card in target.cards) builder.addCards(card.toPbCard())
                        val seq2: Int = p.seq
                        builder.seq = seq2
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq2)) {
                                val builder2 = skill_jing_meng_b_tos.newBuilder()
                                builder2.cardId = target.cards.first().id
                                builder2.seq = seq2
                                p.game!!.tryContinueResolveProtocol(p, builder2.build())
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    val builder = skill_jing_meng_b_tos.newBuilder()
                    builder.cardId = target.cards.first().id
                    r.game!!.tryContinueResolveProtocol(r, builder.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.inFrontOfWhom) {
                log.error("不是你发技能的时机")
                return null
            }
            if (message !is skill_jing_meng_b_tos) {
                log.error("错误的协议")
                return null
            }
            val r = fsm.inFrontOfWhom
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                return null
            }
            val card = target.findCard(message.cardId)
            if (card == null) {
                log.error("没有这张牌")
                return null
            }
            r.incrSeq()
            log.info("${r}弃掉了${target}的$card")
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jing_meng_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.card = card.toPbCard()
                    p.send(builder.build())
                }
            }
            g.playerDiscardCard(target, card)
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(executeJingMengB::class.java)
        }
    }

    companion object {
        fun ai(fsm0: Fsm): Boolean {
            if (fsm0 !is executeJingMengA) return false
            val p = fsm0.fsm.inFrontOfWhom
            val target = p.game!!.players.filter {
                it!!.alive && p.isEnemy(it) && it.cards.isNotEmpty()
            }.randomOrNull() ?: return false
            GameExecutor.post(p.game!!, {
                val builder = skill_jing_meng_a_tos.newBuilder()
                builder.targetPlayerId = p.getAlternativeLocation(target.location)
                p.game!!.tryContinueResolveProtocol(p, builder.build())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}