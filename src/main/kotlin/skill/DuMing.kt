package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.card.filter
import com.fengsheng.phase.*
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
 * 老千技能【赌命】：情报传递到你面前时，或【调包】结算后，若情报是面朝下，你可以声明一种颜色，检视待收情报并面朝下放回，摸一张牌。若猜错且你有黑色手牌，则你必须将一张黑色手牌置入自己的情报区。
 */
class DuMing : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.DU_MING

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm
        if (fsm is OnFinishResolveCard) {
            fsm.cardType == Diao_Bao || return null
            val r = fsm.askWhom
            r.findSkill(skillId) != null || return null
            r.getSkillUseCount(skillId) < 2 || return null
            val fightPhase = fsm.nextFsm as? FightPhaseIdle ?: return null
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
            r.addSkillUseCount(skillId, 2) // 【调包】结算后+2，传递阶段使用+1
            val oldWhereToGoFunc = fsm.whereToGoFunc
            val f = {
                r.resetSkillUseCount(skillId)
                oldWhereToGoFunc()
            }
            return ResolveResult(waitForDuMing(fsm.copy(whereToGoFunc = f), r), true)
        } else if (fsm is SendPhaseIdle) {
            !fsm.isMessageCardFaceUp || return null
            val r = fsm.inFrontOfWhom
            r.findSkill(skillId) != null || return null
            r.getSkillUseCount(skillId) == 0 || return null
            r.addSkillUseCount(skillId)
            return ResolveResult(waitForDuMing(fsm, r), true)
        }
        return null
    }

    private data class waitForDuMing(val fsm: Fsm, val r: Player) : WaitingFsm {
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
            if (fsm is OnFinishResolveCard) {
                val fightPhase = fsm.nextFsm as? FightPhaseIdle
                if (fightPhase == null) {
                    log.error("状态错误：${fsm.nextFsm}")
                    (player as? HumanPlayer)?.sendErrorMessage("服务器内部错误，无法发动技能")
                    return null
                }
                r.incrSeq()
                return ResolveResult(
                    executeDuMing(fsm, fightPhase.whoseTurn, r, message.color, fightPhase.messageCard),
                    true
                )
            } else if (fsm is SendPhaseIdle) {
                r.incrSeq()
                return ResolveResult(
                    executeDuMing(fsm, fsm.whoseTurn, r, message.color, fsm.messageCard),
                    true
                )
            }
            log.error("状态错误：$fsm")
            (player as? HumanPlayer)?.sendErrorMessage("服务器内部错误，无法发动技能")
            return null
        }

        companion object {
            private val log = Logger.getLogger(waitForDuMing::class.java)
        }
    }

    private data class executeDuMing(val fsm: Fsm, val whoseTurn: Player, val r: Player, val c: color, val card: Card) :
        WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game!!
            log.info("${r}发动了赌命，声明了$c")
            r.draw(1)
            val needPutBlack = c !in card.colors && r.cards.any { it.isBlack() }
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
                                    builder2.cardId = p.cards.filter(Black).random().id
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
                    builder2.cardId = r.cards.filter(Black).random().id
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
            if (!card.isBlack()) {
                log.error("这张牌不是黑色")
                (player as? HumanPlayer)?.sendErrorMessage("这张牌不是黑色")
                return null
            }
            r.incrSeq()
            log.info("${r}将${card}置入情报区")
            r.deleteCard(card.id)
            r.messageCards.add(card)
            val newFsm = CheckWin(whoseTurn, fsm)
            newFsm.receiveOrder.addPlayerIfHasThreeBlack(r)
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_du_ming_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.card = card.toPbCard()
                    p.send(builder.build())
                }
            }
            return ResolveResult(OnAddMessageCard(whoseTurn, newFsm), true)
        }

        companion object {
            private val log = Logger.getLogger(executeDuMing::class.java)
        }
    }
}