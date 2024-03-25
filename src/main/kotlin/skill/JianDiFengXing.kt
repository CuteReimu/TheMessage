package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.bestCard
import com.fengsheng.card.Card
import com.fengsheng.protos.*
import com.fengsheng.protos.Fengsheng.end_receive_phase_tos
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 边云疆技能【歼敌风行】：其他玩家收到你的情报后，你可以摸两张牌，将一张纯黑色手牌置入自己的情报区，然后可以弃掉接收到的情报，用一张黑色手牌代替之。
 */
class JianDiFengXing : TriggeredSkill {
    override val skillId = SkillId.JIAN_DI_FENG_XING

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<ReceiveCardEvent>(this) { event ->
            askWhom === event.sender || return@findEvent false
            askWhom !== event.inFrontOfWhom
        } ?: return null
        return ResolveResult(ExecuteJianDiFengXingA(g.fsm!!, event), true)
    }

    private data class ExecuteJianDiFengXingA(val fsm: Fsm, val event: ReceiveCardEvent) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in event.sender.game!!.players)
                p!!.notifyReceivePhase(event.whoseTurn, event.inFrontOfWhom, event.messageCard, event.sender)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== event.sender) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
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
            if (message !is skill_jian_di_feng_xing_a_tos) {
                logger.error("错误的协议")
                player.sendErrorMessage("错误的协议")
                return null
            }
            if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                player.sendErrorMessage("操作太晚了")
                return null
            }
            player.incrSeq()
            logger.info("${player}发动了[歼敌风行]")
            player.draw(2)
            return ResolveResult(ExecuteJianDiFengXingB(fsm, event), true)
        }
    }

    private data class ExecuteJianDiFengXingB(val fsm: Fsm, val event: ReceiveCardEvent) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = event.sender
            val hasBlack = r.cards.any { it.isPureBlack() }
            r.game!!.players.send { p ->
                skillJianDiFengXingAToc {
                    playerId = p.getAlternativeLocation(r.location)
                    if (hasBlack) {
                        waitingSecond = Config.WaitSecond
                        if (p === r) {
                            val seq = p.seq
                            this.seq = seq
                            p.timeout = GameExecutor.post(p.game!!, {
                                if (p.checkSeq(seq)) {
                                    p.game!!.tryContinueResolveProtocol(p, skillJianDiFengXingBTos {
                                        cardId = p.cards.first { it.isPureBlack() }.id
                                        this.seq = seq
                                    })
                                }
                            }, p.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                        }
                    }
                }
            }
            if (hasBlack && r is RobotPlayer) {
                GameExecutor.post(r.game!!, {
                    r.game!!.tryContinueResolveProtocol(r, skillJianDiFengXingBTos {
                        cardId = r.cards.filter { it.isPureBlack() }.random().id
                    })
                }, 3, TimeUnit.SECONDS)
            }
            if (!hasBlack)
                return ResolveResult(fsm, true)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== event.sender) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_jian_di_feng_xing_b_tos) {
                logger.error("错误的协议")
                player.sendErrorMessage("错误的协议")
                return null
            }
            if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                player.sendErrorMessage("操作太晚了")
                return null
            }
            val card = player.findCard(message.cardId)
            if (card == null) {
                logger.error("没有这张牌")
                player.sendErrorMessage("没有这张牌")
                return null
            }
            if (!card.isPureBlack()) {
                logger.error("这张牌不是纯黑色")
                player.sendErrorMessage("这张牌不是纯黑色")
                return null
            }
            player.incrSeq()
            logger.info("${player}将${card}置入情报区")
            player.deleteCard(card.id)
            player.messageCards.add(card)
            player.game!!.addEvent(AddMessageCardEvent(event.whoseTurn))
            return ResolveResult(ExecuteJianDiFengXingC(fsm, event, card), true)
        }
    }

    private data class ExecuteJianDiFengXingC(val fsm: Fsm, val event: ReceiveCardEvent, val card: Card) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = event.sender
            val messageExists = event.inFrontOfWhom.messageCards.any { it.id == event.messageCard.id }
            if (!messageExists) logger.info("待收情报不存在了")
            r.game!!.players.send { p ->
                skillJianDiFengXingBToc {
                    playerId = p.getAlternativeLocation(r.location)
                    card = this@ExecuteJianDiFengXingC.card.toPbCard()
                    if (messageExists) {
                        waitingSecond = Config.WaitSecond
                        if (p === r) {
                            val seq = p.seq
                            this.seq = seq
                            p.timeout = GameExecutor.post(p.game!!, {
                                if (p.checkSeq(seq)) {
                                    p.game!!.tryContinueResolveProtocol(p, skillJianDiFengXingCTos {
                                        enable = false
                                        this.seq = seq
                                    })
                                }
                            }, p.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                        }
                    }
                }
            }
            if (messageExists && r is RobotPlayer) {
                GameExecutor.post(r.game!!, {
                    r.game!!.tryContinueResolveProtocol(r, skillJianDiFengXingCTos {
                        if (r.isEnemy(event.inFrontOfWhom) && !event.messageCard.isBlack()) {
                            val cards = r.cards.filter { it.isBlack() }
                            if (cards.isNotEmpty()) {
                                enable = true
                                cardId = cards.bestCard(r.identity, true).id
                            }
                        }
                    })
                }, 3, TimeUnit.SECONDS)
            }
            if (!messageExists)
                return ResolveResult(fsm, true)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== event.sender) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_jian_di_feng_xing_c_tos) {
                logger.error("错误的协议")
                player.sendErrorMessage("错误的协议")
                return null
            }
            if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                player.sendErrorMessage("操作太晚了")
                return null
            }
            if (!message.enable) {
                player.incrSeq()
                player.game!!.players.send {
                    skillJianDiFengXingCToc {
                        playerId = it.getAlternativeLocation(player.location)
                        enable = false
                    }
                }
                return ResolveResult(fsm, true)
            }
            val card = player.deleteCard(message.cardId)
            if (card == null) {
                logger.error("没有这张牌")
                player.sendErrorMessage("没有这张牌")
                return null
            }
            if (!card.isBlack()) {
                logger.error("这张牌不是黑色")
                player.sendErrorMessage("这张牌不是黑色")
                return null
            }
            val target = event.inFrontOfWhom
            player.incrSeq()
            logger.info("${player}将${target}面前的${event.messageCard}弃掉，并用${card}代替之")
            target.deleteMessageCard(event.messageCard.id)
            player.game!!.deck.discard(event.messageCard)
            target.messageCards.add(card)
            player.game!!.players.send {
                skillJianDiFengXingCToc {
                    playerId = it.getAlternativeLocation(player.location)
                    enable = true
                    this.card = card.toPbCard()
                    oldMessageCardId = event.messageCard.id
                }
            }
            event.messageCard = card
            return ResolveResult(fsm, true)
        }
    }

    companion object {
        fun ai(fsm: Fsm): Boolean {
            if (fsm !is ExecuteJianDiFengXingA) return false
            val p = fsm.event.sender
            GameExecutor.post(p.game!!, {
                p.game!!.tryContinueResolveProtocol(p, skillJianDiFengXingATos { })
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}
