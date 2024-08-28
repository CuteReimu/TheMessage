package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.bestCard
import com.fengsheng.card.Card
import com.fengsheng.card.count
import com.fengsheng.card.countTrueCard
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.*
import com.fengsheng.protos.Common.card_type.Diao_Bao
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Common.secret_task.*
import com.fengsheng.protos.Role.skill_du_ming_a_tos
import com.fengsheng.protos.Role.skill_du_ming_b_tos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 金自来技能【赌命】：一回合一次，情报传递到你面前时，或【调包】结算后，若情报是面朝下，你可以声明一种颜色，检视待收情报并面朝下放回，摸一张牌。若猜错且你有纯黑色手牌，则你必须将一张纯黑色手牌置入自己的情报区。
 */
class DuMing : TriggeredSkill {
    override val skillId = SkillId.DU_MING

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event1 = g.findEvent<FinishResolveCardEvent>(this) { event ->
            event.cardType == Diao_Bao || return@findEvent false
            askWhom.getSkillUseCount(skillId) == 0
        }
        if (event1 != null)
            return ResolveResult(WaitForDuMing(event1.nextFsm, event1, askWhom), true)
        val event2 = g.findEvent<MessageMoveNextEvent>(this) { event ->
            !event.isMessageCardFaceUp || return@findEvent false
            askWhom === event.inFrontOfWhom || return@findEvent false
            askWhom.getSkillUseCount(skillId) == 0
        }
        if (event2 != null)
            return ResolveResult(WaitForDuMing(g.fsm!!, event2, askWhom), true)
        return null
    }

    private data class WaitForDuMing(val fsm: Fsm, val event: Event, val r: Player) : WaitingFsm {
        override val whoseTurn: Player
            get() = fsm.whoseTurn

        override fun resolve(): ResolveResult? {
            val g = r.game!!
            g.players.send { p ->
                skillWaitForDuMingToc {
                    playerId = p.getAlternativeLocation(r.location)
                    waitingSecond = g.waitSecond
                    if (p === r) {
                        val seq = p.seq
                        this.seq = seq
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq))
                                g.tryContinueResolveProtocol(p, skillDuMingATos { this.seq = seq })
                        }, p.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                }
            }
            if (r is RobotPlayer) {
                val (messageCard, causer) = when (event) {
                    is FinishResolveCardEvent -> {
                        val fightPhase = event.nextFsm as? FightPhaseIdle
                        if (fightPhase == null) {
                            logger.error("状态错误：${event.nextFsm}")
                            throw IllegalStateException()
                        } else {
                            fightPhase.messageCard to event.player
                        }
                    }

                    is MessageMoveNextEvent -> event.messageCard to event.whoseTurn
                    else -> {
                        logger.error("状态错误：$event")
                        throw IllegalStateException()
                    }
                }
                GameExecutor.post(g, {
                    g.tryContinueResolveProtocol(r, skillDuMingATos {
                        enable = true
                        if (if (r.identity == Black) r !== causer && Random.nextBoolean()
                            else causer.identity != r.identity
                        ) {
                            // 适当提高猜对几率
                            color = (listOf(Red, Blue, Black) + messageCard.colors).random()
                        } else {
                            var wrong = false
                            if (r.identity == Black) {
                                when (r.secretTask) {
                                    Killer -> {
                                        if (r.messageCards.count(Black) < 2 ||
                                            r === event.whoseTurn && r.messageCards.countTrueCard() >= 2
                                        ) wrong = true
                                    }

                                    Pioneer -> {
                                        if (r.messageCards.count(Black) < 2 || r.messageCards.countTrueCard() >= 1)
                                            wrong = true
                                    }

                                    Sweeper -> {
                                        if (r.messageCards.count(Black) < 2 ||
                                            r.messageCards.run { count(Red) <= 1 && count(Blue) <= 1 }
                                        ) wrong = true
                                    }

                                    else -> {}
                                }
                            }
                            color = listOf(Red, Blue, Black).filter { it !in messageCard.colors == wrong }.run {
                                firstOrNull { r.identity != Black && it == r.identity }
                                    ?: firstOrNull { it != Black } ?: random()
                            }
                        }
                    })
                }, 3, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== r) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_du_ming_a_tos) {
                logger.error("错误的协议")
                player.sendErrorMessage("错误的协议")
                return null
            }
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            if (!message.enable) {
                r.incrSeq()
                return ResolveResult(fsm, true)
            }
            if (message.color !== Red && message.color !== Blue && message.color !== Black) {
                logger.error("不存在的颜色")
                player.sendErrorMessage("不存在的颜色")
                return null
            }
            if (event is FinishResolveCardEvent) {
                val fightPhase = event.nextFsm as? FightPhaseIdle
                if (fightPhase == null) {
                    logger.error("状态错误：${event.nextFsm}")
                    player.sendErrorMessage("服务器内部错误，无法发动技能")
                    return null
                }
                r.incrSeq()
                return ResolveResult(ExecuteDuMing(fsm, event, r, message.color, fightPhase.messageCard), true)
            } else if (event is MessageMoveNextEvent) {
                r.incrSeq()
                return ResolveResult(ExecuteDuMing(fsm, event, r, message.color, event.messageCard), true)
            }
            logger.error("状态错误：$fsm")
            player.sendErrorMessage("服务器内部错误，无法发动技能")
            return null
        }
    }

    private data class ExecuteDuMing(
        val fsm: Fsm,
        val event: Event,
        val r: Player,
        val c: color,
        val card: Card
    ) : WaitingFsm {
        override val whoseTurn: Player
            get() = fsm.whoseTurn

        override fun resolve(): ResolveResult? {
            val g = r.game!!
            r.addSkillUseCount(SkillId.DU_MING)
            logger.info("${r}发动了赌命，声明了$c")
            r.draw(1)
            val needPutBlack = c !in card.colors && r.cards.any { it.isPureBlack() }
            g.players.send { p ->
                skillDuMingAToc {
                    playerId = p.getAlternativeLocation(r.location)
                    color = c
                    if (p === r) card = this@ExecuteDuMing.card.toPbCard()
                    if (needPutBlack) {
                        waitingSecond = g.waitSecond
                        if (p === r) {
                            val seq = p.seq
                            this.seq = seq
                            p.timeout = GameExecutor.post(g, {
                                if (p.checkSeq(seq)) {
                                    g.tryContinueResolveProtocol(p, skillDuMingBTos {
                                        cardId = p.cards.filter { it.isPureBlack() }.random().id
                                        this.seq = seq
                                    })
                                }
                            }, p.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                        }
                    }
                }
            }
            if (!needPutBlack)
                return ResolveResult(fsm, true)
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    g.tryContinueResolveProtocol(r, skillDuMingBTos {
                        cardId = r.cards.filter { it.isPureBlack() }.bestCard(r.identity, true).id
                    })
                }, 3, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== r) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_du_ming_b_tos) {
                logger.error("错误的协议")
                player.sendErrorMessage("错误的协议")
                return null
            }
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            val card = r.findCard(message.cardId)
            if (card == null) {
                logger.error("没有这张牌")
                player.sendErrorMessage("没有这张牌")
                return null
            }
            if (!card.isPureBlack()) {
                logger.error("这张牌不是纯黑色")
                player.sendErrorMessage("这张牌不是黑色")
                return null
            }
            r.incrSeq()
            logger.info("${r}将${card}置入情报区")
            r.deleteCard(card.id)
            r.messageCards.add(card)
            r.game!!.players.send {
                skillDuMingBToc {
                    playerId = it.getAlternativeLocation(r.location)
                    this.card = card.toPbCard()
                }
            }
            r.game!!.addEvent(AddMessageCardEvent(event.whoseTurn))
            return ResolveResult(fsm, true)
        }
    }
}
