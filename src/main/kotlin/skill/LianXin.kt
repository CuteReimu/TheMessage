package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.protos.Fengsheng.end_receive_phase_tos
import com.fengsheng.protos.Fengsheng.unknown_waiting_toc
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 成年小九技能【联信】：接收其他角色情报后，可以翻开此角色，摸两张牌，然后将一张含该情报不同颜色的手牌置入传出者的情报区。
 */
class LianXin : InitialSkill, TriggeredSkill {
    override val skillId = SkillId.LIAN_XIN

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<ReceiveCardEvent>(this) { event ->
            askWhom === event.inFrontOfWhom || return@findEvent false
            askWhom !== event.sender || return@findEvent false
            !askWhom.roleFaceUp
        } ?: return null
        val color = event.messageCard.colors
        return ResolveResult(executeLianXinA(g.fsm!!, event) { card -> card.colors.any { it !in color } }, true)
    }

    private data class executeLianXinA(val fsm: Fsm, val event: ReceiveCardEvent, val checkCard: (Card) -> Boolean) :
        WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in event.whoseTurn.game!!.players) {
                if (p === event.inFrontOfWhom) {
                    p.notifyReceivePhase(event.whoseTurn, event.inFrontOfWhom, event.messageCard, event.inFrontOfWhom)
                } else if (p is HumanPlayer) {
                    val builder = unknown_waiting_toc.newBuilder()
                    builder.waitingSecond = Config.WaitSecond
                    p.send(builder.build())
                }
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== event.inFrontOfWhom) {
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
            if (message !is skill_lian_xin_a_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val r = event.inFrontOfWhom
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            r.incrSeq()
            g.playerSetRoleFaceUp(r, true)
            val target = event.sender
            log.info("${r}发动了[联信]")
            r.draw(2)
            val hasNext = target.alive && r.cards.any(checkCard)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_lian_xin_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.messageCard = event.messageCard.toPbCard()
                    if (hasNext) {
                        builder.waitingSecond = Config.WaitSecond
                        if (p === r) builder.seq = p.seq
                    }
                    p.send(builder.build())
                }
            }
            if (!hasNext)
                return ResolveResult(fsm, true)
            return ResolveResult(executeLianXinB(fsm, event, checkCard), true)
        }

        companion object {
            private val log = Logger.getLogger(executeLianXinA::class.java)
        }
    }

    private data class executeLianXinB(val fsm: Fsm, val event: ReceiveCardEvent, val checkCard: (Card) -> Boolean) :
        WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = event.inFrontOfWhom
            val card = r.cards.filter(checkCard).random()
            if (r is HumanPlayer) {
                val seq = r.seq
                r.timeout = GameExecutor.post(r.game!!, {
                    if (r.checkSeq(seq)) {
                        val builder = skill_lian_xin_b_tos.newBuilder()
                        builder.cardId = card.id
                        builder.seq = seq
                        r.game!!.tryContinueResolveProtocol(r, builder.build())
                    }
                }, r.getWaitSeconds(Config.WaitSecond + 2).toLong(), TimeUnit.SECONDS)
            } else {
                GameExecutor.post(r.game!!, {
                    val builder = skill_lian_xin_b_tos.newBuilder()
                    builder.cardId = card.id
                    r.game!!.tryContinueResolveProtocol(r, builder.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== event.inFrontOfWhom) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_lian_xin_b_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val r = event.inFrontOfWhom
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
                log.error("选择的牌不含有不同颜色")
                (player as? HumanPlayer)?.sendErrorMessage("选择的牌不含有不同颜色")
                return null
            }
            r.incrSeq()
            g.playerSetRoleFaceUp(r, true)
            val target = event.sender
            log.info("${r}将${card}置入${target}的情报区")
            r.deleteCard(card.id)
            target.messageCards.add(card)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_lian_xin_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.card = card.toPbCard()
                    p.send(builder.build())
                }
            }
            g.addEvent(AddMessageCardEvent(event.whoseTurn))
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(executeLianXinB::class.java)
        }
    }

    companion object {
        fun ai(fsm0: Fsm): Boolean {
            if (fsm0 !is executeLianXinA) return false
            val p = fsm0.event.inFrontOfWhom
            GameExecutor.post(p.game!!, {
                p.game!!.tryContinueResolveProtocol(p, skill_lian_xin_a_tos.getDefaultInstance())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}