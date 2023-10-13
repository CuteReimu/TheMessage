package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.protos.Fengsheng.end_receive_phase_tos
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * SP小九技能【赤子之心】：你传出的非黑色情报被其他角色接收后，你可以摸两张牌，或从手牌中选择一张含有该情报颜色的牌，将其置入你的情报区。
 */
class ChiZiZhiXin : InitialSkill, TriggeredSkill {
    override val skillId = SkillId.CHI_ZI_ZHI_XIN

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<ReceiveCardEvent>(this) { event ->
            askWhom === event.sender || return@findEvent false
            !event.messageCard.isBlack() || return@findEvent false
            askWhom !== event.inFrontOfWhom
        } ?: return null
        return ResolveResult(executeChiZiZhiXinA(g.fsm!!, event), true)
    }

    private data class executeChiZiZhiXinA(val fsm: Fsm, val event: ReceiveCardEvent) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in event.sender.game!!.players)
                p!!.notifyReceivePhase(
                    event.whoseTurn,
                    event.inFrontOfWhom,
                    event.messageCard,
                    event.sender
                )
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== event.sender) {
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
            if (message !is skill_chi_zi_zhi_xin_a_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val r = event.sender
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            player.incrSeq()
            return ResolveResult(executeChiZiZhiXinB(fsm, event), true)
        }

        companion object {
            private val log = Logger.getLogger(executeChiZiZhiXinA::class.java)
        }
    }

    private data class executeChiZiZhiXinB(val fsm: Fsm, val event: ReceiveCardEvent) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = event.sender
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_chi_zi_zhi_xin_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.messageCard = event.messageCard.toPbCard()
                    builder.waitingSecond = Config.WaitSecond
                    if (p === r) {
                        val seq = r.seq
                        builder.seq = seq
                        p.timeout = GameExecutor.post(r.game!!, {
                            if (p.checkSeq(seq)) {
                                val builder2 = skill_chi_zi_zhi_xin_b_tos.newBuilder()
                                builder2.drawCard = true
                                builder2.seq = seq
                                p.game!!.tryContinueResolveProtocol(p, builder2.build())
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(r.game!!, {
                    val builder2 = skill_chi_zi_zhi_xin_b_tos.newBuilder()
                    builder2.drawCard = true
                    r.game!!.tryContinueResolveProtocol(r, builder2.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== event.sender) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_chi_zi_zhi_xin_b_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val r = event.sender
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            var card: Card? = null
            if (!message.drawCard) {
                card = r.findCard(message.cardId)
                if (card == null) {
                    log.error("没有这张卡")
                    (player as? HumanPlayer)?.sendErrorMessage("没有这张卡")
                    return null
                }
                if (!card.hasSameColor(event.messageCard)) {
                    log.error("你选择的牌没有情报牌的颜色")
                    (player as? HumanPlayer)?.sendErrorMessage("你选择的牌没有情报牌的颜色")
                    return null
                }
                log.info("${r}发动了[赤子之心]，将手牌中的${card}置入自己的情报区")
                r.incrSeq()
                r.deleteCard(card.id)
                r.messageCards.add(card)
                r.game!!.addEvent(AddMessageCardEvent(event.whoseTurn))
            } else {
                log.info("${r}发动了[赤子之心]，选择了摸两张牌")
                r.incrSeq()
            }
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_chi_zi_zhi_xin_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.drawCard = message.drawCard
                    if (card != null) builder.card = card.toPbCard()
                    p.send(builder.build())
                }
            }
            if (message.drawCard) r.draw(2)
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(executeChiZiZhiXinB::class.java)
        }
    }

    companion object {
        fun ai(fsm0: Fsm): Boolean {
            if (fsm0 !is executeChiZiZhiXinA) return false
            val p = fsm0.event.sender
            GameExecutor.post(p.game!!, {
                p.game!!.tryContinueResolveProtocol(p, skill_chi_zi_zhi_xin_a_tos.getDefaultInstance())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}