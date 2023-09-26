package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.phase.OnAddMessageCard
import com.fengsheng.phase.ReceivePhaseSkill
import com.fengsheng.protos.Fengsheng.end_receive_phase_tos
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 军人技能【歼敌风行】：其他玩家收到你的情报后，你可以摸两张牌，将一张纯黑色手牌置入自己的情报区，然后可以弃掉接收到的情报，用一张黑色手牌代替之。
 */
class JianDiFengXing : InitialSkill, TriggeredSkill {
    override val skillId = SkillId.JIAN_DI_FENG_XING

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val fsm = g.fsm as? ReceivePhaseSkill ?: return null
        askWhom === fsm.sender || return null
        askWhom !== fsm.inFrontOfWhom || return null
        fsm.sender.getSkillUseCount(skillId) == 0 || return null
        fsm.sender.addSkillUseCount(skillId)
        return ResolveResult(executeJianDiFengXingA(fsm), true)
    }

    private data class executeJianDiFengXingA(val fsm: ReceivePhaseSkill) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in fsm.sender.game!!.players)
                p!!.notifyReceivePhase(fsm.whoseTurn, fsm.inFrontOfWhom, fsm.messageCard, fsm.sender)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.sender) {
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
            return ResolveResult(executeJianDiFengXingB(fsm), true)
        }

        companion object {
            private val log = Logger.getLogger(executeJianDiFengXingA::class.java)
        }
    }

    private data class executeJianDiFengXingB(val fsm: ReceivePhaseSkill) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = fsm.sender
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
            if (player !== fsm.sender) {
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
            fsm.receiveOrder.addPlayerIfHasThreeBlack(player)
            return ResolveResult(executeJianDiFengXingC(fsm, card), true)
        }

        companion object {
            private val log = Logger.getLogger(executeJianDiFengXingB::class.java)
        }
    }

    private data class executeJianDiFengXingC(val fsm: ReceivePhaseSkill, val card: Card) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = fsm.sender
            val messageExists = fsm.inFrontOfWhom.messageCards.any { it.id == fsm.messageCard.id }
            if (!messageExists) log.warn("待收情报不存在了")
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
                    builder2.enable = true
                    builder2.cardId = r.cards.random().id
                    r.game!!.tryContinueResolveProtocol(r, builder2.build())
                }, 2, TimeUnit.SECONDS)
            }
            if (!messageExists)
                return ResolveResult(OnAddMessageCard(fsm.whoseTurn, fsm), true)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.sender) {
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
                return ResolveResult(OnAddMessageCard(fsm.whoseTurn, fsm), true)
            }
            val card = player.deleteCard(message.cardId)
            if (card == null) {
                log.error("没有这张牌")
                (player as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                return null
            }
            val target = fsm.inFrontOfWhom
            player.incrSeq()
            log.info("${player}将${target}面前的${fsm.messageCard}弃掉，并用${card}代替之")
            target.deleteMessageCard(fsm.messageCard.id)
            player.game!!.deck.discard(fsm.messageCard)
            fsm.receiveOrder.removePlayerIfNotHaveThreeBlack(target)
            target.messageCards.add(card)
            fsm.receiveOrder.addPlayerIfHasThreeBlack(target)
            for (p in player.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jian_di_feng_xing_c_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(player.location)
                    builder.enable = true
                    builder.card = card.toPbCard()
                    builder.oldMessageCardId = fsm.messageCard.id
                    p.send(builder.build())
                }
            }
            return ResolveResult(executeJianDiFengXingB(fsm.copy(messageCard = card)), true)
        }

        companion object {
            private val log = Logger.getLogger(executeJianDiFengXingB::class.java)
        }
    }

    companion object {
        fun ai(fsm: Fsm): Boolean {
            if (fsm !is executeJianDiFengXingA) return false
            val p = fsm.fsm.sender
            GameExecutor.post(p.game!!, {
                val builder = skill_jian_di_feng_xing_a_tos.newBuilder()
                p.game!!.tryContinueResolveProtocol(p, builder.build())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}