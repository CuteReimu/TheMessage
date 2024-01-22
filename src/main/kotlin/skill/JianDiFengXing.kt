package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.protos.Fengsheng.end_receive_phase_tos
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
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
        return ResolveResult(executeJianDiFengXingA(g.fsm!!, event), true)
    }

    private data class executeJianDiFengXingA(val fsm: Fsm, val event: ReceiveCardEvent) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in event.sender.game!!.players)
                p!!.notifyReceivePhase(event.whoseTurn, event.inFrontOfWhom, event.messageCard, event.sender)
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
            if (message !is skill_jian_di_feng_xing_a_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                player.sendErrorMessage("操作太晚了")
                return null
            }
            player.incrSeq()
            log.info("${player}发动了[歼敌风行]")
            player.draw(2)
            return ResolveResult(executeJianDiFengXingB(fsm, event), true)
        }

        companion object {
            private val log = Logger.getLogger(executeJianDiFengXingA::class.java)
        }
    }

    private data class executeJianDiFengXingB(val fsm: Fsm, val event: ReceiveCardEvent) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = event.sender
            val hasBlack = r.cards.any { it.isPureBlack() }
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jian_di_feng_xing_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    if (hasBlack) {
                        builder.waitingSecond = Config.WaitSecond
                        if (p === r) {
                            val seq = p.seq
                            builder.seq = seq
                            p.timeout = GameExecutor.post(p.game!!, {
                                if (p.checkSeq(seq)) {
                                    val builder2 = skill_jian_di_feng_xing_b_tos.newBuilder()
                                    builder2.cardId = p.cards.first { it.isPureBlack() }.id
                                    builder2.seq = seq
                                    p.game!!.tryContinueResolveProtocol(p, builder2.build())
                                }
                            }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                        }
                    }
                    p.send(builder.build())
                }
            }
            if (hasBlack && r is RobotPlayer) {
                GameExecutor.post(r.game!!, {
                    val builder2 = skill_jian_di_feng_xing_b_tos.newBuilder()
                    builder2.cardId = r.cards.filter { it.isPureBlack() }.random().id
                    r.game!!.tryContinueResolveProtocol(r, builder2.build())
                }, 2, TimeUnit.SECONDS)
            }
            if (!hasBlack)
                return ResolveResult(fsm, true)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== event.sender) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_jian_di_feng_xing_b_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                player.sendErrorMessage("操作太晚了")
                return null
            }
            val card = player.findCard(message.cardId)
            if (card == null) {
                log.error("没有这张牌")
                (player as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                return null
            }
            if (!card.isPureBlack()) {
                log.error("这张牌不是纯黑色")
                (player as? HumanPlayer)?.sendErrorMessage("这张牌不是纯黑色")
                return null
            }
            player.incrSeq()
            log.info("${player}将${card}置入情报区")
            player.deleteCard(card.id)
            player.messageCards.add(card)
            player.game!!.addEvent(AddMessageCardEvent(event.whoseTurn))
            return ResolveResult(executeJianDiFengXingC(fsm, event, card), true)
        }

        companion object {
            private val log = Logger.getLogger(executeJianDiFengXingB::class.java)
        }
    }

    private data class executeJianDiFengXingC(val fsm: Fsm, val event: ReceiveCardEvent, val card: Card) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = event.sender
            val messageExists = event.inFrontOfWhom.messageCards.any { it.id == event.messageCard.id }
            if (!messageExists) log.info("待收情报不存在了")
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jian_di_feng_xing_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.card = card.toPbCard()
                    if (messageExists) {
                        builder.waitingSecond = Config.WaitSecond
                        if (p === r) {
                            val seq = p.seq
                            builder.seq = seq
                            p.timeout = GameExecutor.post(p.game!!, {
                                if (p.checkSeq(seq)) {
                                    val builder2 = skill_jian_di_feng_xing_c_tos.newBuilder()
                                    builder2.enable = false
                                    builder2.seq = seq
                                    p.game!!.tryContinueResolveProtocol(p, builder2.build())
                                }
                            }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                        }
                    }
                    p.send(builder.build())
                }
            }
            if (messageExists && r is RobotPlayer) {
                GameExecutor.post(r.game!!, {
                    val builder2 = skill_jian_di_feng_xing_c_tos.newBuilder()
                    r.cards.filter { it.isBlack() }.randomOrNull()?.let { card ->
                        builder2.enable = true
                        builder2.cardId = card.id
                    }
                    r.game!!.tryContinueResolveProtocol(r, builder2.build())
                }, 2, TimeUnit.SECONDS)
            }
            if (!messageExists)
                return ResolveResult(fsm, true)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== event.sender) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_jian_di_feng_xing_c_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                player.sendErrorMessage("操作太晚了")
                return null
            }
            if (!message.enable) {
                player.incrSeq()
                for (p in player.game!!.players) {
                    if (p is HumanPlayer) {
                        val builder = skill_jian_di_feng_xing_c_toc.newBuilder()
                        builder.playerId = p.getAlternativeLocation(player.location)
                        builder.enable = false
                        p.send(builder.build())
                    }
                }
                return ResolveResult(fsm, true)
            }
            val card = player.deleteCard(message.cardId)
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
            val target = event.inFrontOfWhom
            player.incrSeq()
            log.info("${player}将${target}面前的${event.messageCard}弃掉，并用${card}代替之")
            target.deleteMessageCard(event.messageCard.id)
            player.game!!.deck.discard(event.messageCard)
            target.messageCards.add(card)
            for (p in player.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jian_di_feng_xing_c_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(player.location)
                    builder.enable = true
                    builder.card = card.toPbCard()
                    builder.oldMessageCardId = event.messageCard.id
                    p.send(builder.build())
                }
            }
            event.messageCard = card
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(executeJianDiFengXingB::class.java)
        }
    }

    companion object {
        fun ai(fsm: Fsm): Boolean {
            if (fsm !is executeJianDiFengXingA) return false
            val p = fsm.event.sender
            GameExecutor.post(p.game!!, {
                val builder = skill_jian_di_feng_xing_a_tos.newBuilder()
                p.game!!.tryContinueResolveProtocol(p, builder.build())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}