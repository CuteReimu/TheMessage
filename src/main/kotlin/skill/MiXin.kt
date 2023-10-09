package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.phase.ReceivePhaseSkill
import com.fengsheng.protos.Fengsheng.end_receive_phase_tos
import com.fengsheng.protos.Fengsheng.unknown_waiting_toc
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 成年韩梅技能【密信】：接收其他角色情报后，可以翻开此角色，摸两张牌，然后将一张含该情报相同颜色的手牌置入传出者的情报区。
 */
class MiXin : InitialSkill, TriggeredSkill {
    override val skillId = SkillId.MI_XIN

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val fsm = g.fsm as? ReceivePhaseSkill ?: return null
        askWhom === fsm.inFrontOfWhom || return null
        askWhom !== fsm.sender || return null
        !askWhom.roleFaceUp || return null
        askWhom.getSkillUseCount(skillId) == 0 || return null
        askWhom.addSkillUseCount(skillId)
        val color = fsm.messageCard.colors
        return ResolveResult(executeMiXinA(fsm) { card -> card.colors.any { it in color } }, true)
    }

    private data class executeMiXinA(val fsm: ReceivePhaseSkill, val checkCard: (Card) -> Boolean) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in fsm.whoseTurn.game!!.players) {
                if (p === fsm.inFrontOfWhom) {
                    p.notifyReceivePhase(fsm.whoseTurn, fsm.inFrontOfWhom, fsm.messageCard, fsm.inFrontOfWhom)
                } else if (p is HumanPlayer) {
                    val builder = unknown_waiting_toc.newBuilder()
                    builder.waitingSecond = Config.WaitSecond
                    p.send(builder.build())
                }
            }
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
            if (message !is skill_mi_xin_a_tos) {
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
            r.incrSeq()
            g.playerSetRoleFaceUp(r, true)
            val target = fsm.sender
            log.info("${r}发动了[密信]")
            r.draw(2)
            val hasNext = target.alive && r.cards.any(checkCard)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_mi_xin_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.messageCard = fsm.messageCard.toPbCard()
                    if (hasNext) {
                        builder.waitingSecond = Config.WaitSecond
                        if (p === r) builder.seq = p.seq
                    }
                    p.send(builder.build())
                }
            }
            if (!hasNext)
                return ResolveResult(fsm, true)
            return ResolveResult(executeMiXinB(fsm, checkCard), true)
        }

        companion object {
            private val log = Logger.getLogger(executeMiXinA::class.java)
        }
    }

    private data class executeMiXinB(val fsm: ReceivePhaseSkill, val checkCard: (Card) -> Boolean) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = fsm.inFrontOfWhom
            val card = r.cards.filter(checkCard).random()
            if (r is HumanPlayer) {
                val seq = r.seq
                r.timeout = GameExecutor.post(r.game!!, {
                    if (r.checkSeq(seq)) {
                        val builder = skill_mi_xin_b_tos.newBuilder()
                        builder.cardId = card.id
                        builder.seq = seq
                        r.game!!.tryContinueResolveProtocol(r, builder.build())
                    }
                }, r.getWaitSeconds(Config.WaitSecond + 2).toLong(), TimeUnit.SECONDS)
            } else {
                GameExecutor.post(r.game!!, {
                    val builder = skill_mi_xin_b_tos.newBuilder()
                    builder.cardId = card.id
                    r.game!!.tryContinueResolveProtocol(r, builder.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.inFrontOfWhom) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_mi_xin_b_tos) {
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
                (player as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                return null
            }
            if (!checkCard(card)) {
                log.error("选择的牌不含有相同颜色")
                (player as? HumanPlayer)?.sendErrorMessage("选择的牌不含有不同颜色")
                return null
            }
            r.incrSeq()
            g.playerSetRoleFaceUp(r, true)
            val target = fsm.sender
            log.info("${r}将${card}置入${target}的情报区")
            r.deleteCard(card.id)
            target.messageCards.add(card)
            fsm.receiveOrder.addPlayerIfHasThreeBlack(target)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_mi_xin_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.card = card.toPbCard()
                    p.send(builder.build())
                }
            }
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(executeMiXinB::class.java)
        }
    }

    companion object {
        fun ai(fsm0: Fsm): Boolean {
            if (fsm0 !is executeMiXinA) return false
            val p = fsm0.fsm.inFrontOfWhom
            GameExecutor.post(p.game!!, {
                p.game!!.tryContinueResolveProtocol(p, skill_mi_xin_a_tos.getDefaultInstance())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}