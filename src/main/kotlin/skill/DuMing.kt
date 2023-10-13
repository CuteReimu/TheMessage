package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Common.card_type.Diao_Bao
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Common.phase.Fight_Phase
import com.fengsheng.protos.Fengsheng
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 金自来技能【赌命】：一回合一次，情报传递到你面前时，或【调包】结算后，若情报是面朝下，你可以声明一种颜色，检视待收情报并面朝下放回，摸一张牌。若猜错且你有纯黑色手牌，则你必须将一张纯黑色手牌置入自己的情报区。
 */
class DuMing : InitialSkill, TriggeredSkill {
    override val skillId = SkillId.DU_MING

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event1 = g.findEvent<FinishResolveCardEvent>(this) { event ->
            event.cardType == Diao_Bao || return@findEvent false
            askWhom.getSkillUseCount(skillId) == 0
        }
        if (event1 != null) {
            val fightPhase = event1.nextFsm as? FightPhaseIdle ?: return null
            for (p in g.players) { // 解决客户端动画问题
                if (p is HumanPlayer) {
                    val builder = Fengsheng.notify_phase_toc.newBuilder()
                    builder.currentPlayerId = p.getAlternativeLocation(fightPhase.whoseTurn.location)
                    builder.messagePlayerId = p.getAlternativeLocation(fightPhase.inFrontOfWhom.location)
                    builder.waitingPlayerId = p.getAlternativeLocation(fightPhase.whoseFightTurn.location)
                    builder.currentPhase = Fight_Phase
                    if (fightPhase.isMessageCardFaceUp)
                        builder.messageCard = fightPhase.messageCard.toPbCard()
                    p.send(builder.build())
                }
            }
            askWhom.addSkillUseCount(skillId, 2)
            return ResolveResult(waitForDuMing(fightPhase, event1, askWhom), true)
        }
        val event2 = g.findEvent<MessageMoveNextEvent>(this) { event ->
            !event.isMessageCardFaceUp || return@findEvent false
            askWhom === event.inFrontOfWhom || return@findEvent false
            askWhom.getSkillUseCount(skillId) == 0
        }
        if (event2 != null)
            return ResolveResult(waitForDuMing(g.fsm!!, event2, askWhom), true)
        return null
    }

    private data class waitForDuMing(val fsm: Fsm, val event: Event, val r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game!!
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_wait_for_du_ming_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.waitingSecond = Config.WaitSecond
                    if (p === r) {
                        val seq = p.seq
                        builder.seq = seq
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq)) {
                                val builder2 = skill_du_ming_a_tos.newBuilder()
                                builder2.enable = false
                                builder2.seq = seq
                                g.tryContinueResolveProtocol(p, builder2.build())
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    val builder2 = skill_du_ming_a_tos.newBuilder()
                    builder2.enable = true
                    builder2.color = arrayOf(Red, Blue, Black).random()
                    g.tryContinueResolveProtocol(r, builder2.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_du_ming_a_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            if (!message.enable) {
                r.incrSeq()
                return ResolveResult(fsm, true)
            }
            if (message.color !== Red && message.color !== Blue && message.color !== Black) {
                log.error("不存在的颜色")
                (player as? HumanPlayer)?.sendErrorMessage("不存在的颜色")
                return null
            }
            if (event is FinishResolveCardEvent) {
                val fightPhase = event.nextFsm as? FightPhaseIdle
                if (fightPhase == null) {
                    log.error("状态错误：${event.nextFsm}")
                    (player as? HumanPlayer)?.sendErrorMessage("服务器内部错误，无法发动技能")
                    return null
                }
                r.incrSeq()
                return ResolveResult(executeDuMing(fsm, event, r, message.color, fightPhase.messageCard), true)
            } else if (event is MessageMoveNextEvent) {
                r.incrSeq()
                return ResolveResult(executeDuMing(fsm, event, r, message.color, event.messageCard), true)
            }
            log.error("状态错误：$fsm")
            (player as? HumanPlayer)?.sendErrorMessage("服务器内部错误，无法发动技能")
            return null
        }

        companion object {
            private val log = Logger.getLogger(waitForDuMing::class.java)
        }
    }

    private data class executeDuMing(
        val fsm: Fsm,
        val event: Event,
        val r: Player,
        val c: color,
        val card: Card
    ) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game!!
            r.addSkillUseCount(SkillId.DU_MING)
            log.info("${r}发动了赌命，声明了$c")
            r.draw(1)
            val needPutBlack = c !in card.colors && r.cards.any { it.isPureBlack() }
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_du_ming_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.color = c
                    if (p === r) builder.card = card.toPbCard()
                    if (needPutBlack) {
                        builder.waitingSecond = Config.WaitSecond
                        if (p === r) {
                            val seq = p.seq
                            builder.seq = seq
                            p.timeout = GameExecutor.post(g, {
                                if (p.checkSeq(seq)) {
                                    val builder2 = skill_du_ming_b_tos.newBuilder()
                                    builder2.cardId = p.cards.filter { it.isPureBlack() }.random().id
                                    builder2.seq = seq
                                    g.tryContinueResolveProtocol(p, builder2.build())
                                }
                            }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                        }
                    }
                    p.send(builder.build())
                }
            }
            if (!needPutBlack)
                return ResolveResult(fsm, true)
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    val builder2 = skill_du_ming_b_tos.newBuilder()
                    builder2.cardId = r.cards.filter { it.isPureBlack() }.random().id
                    g.tryContinueResolveProtocol(r, builder2.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_du_ming_b_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
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
            if (!card.isPureBlack()) {
                log.error("这张牌不是纯黑色")
                (player as? HumanPlayer)?.sendErrorMessage("这张牌不是黑色")
                return null
            }
            r.incrSeq()
            log.info("${r}将${card}置入情报区")
            r.deleteCard(card.id)
            r.messageCards.add(card)
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_du_ming_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.card = card.toPbCard()
                    p.send(builder.build())
                }
            }
            r.game!!.addEvent(AddMessageCardEvent(event.whoseTurn))
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(executeDuMing::class.java)
        }
    }
}