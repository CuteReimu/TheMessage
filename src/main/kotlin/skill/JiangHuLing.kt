package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.protos.Common
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Fengsheng.end_receive_phase_tos
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 王富贵技能【江湖令】：你传出情报后，可以宣言一个颜色。本回合中，当情报被接收后，你可以从接收者的情报区弃置一张被宣言颜色的情报，若弃置的是黑色情报，则你摸一张牌。
 */
class JiangHuLing : TriggeredSkill {
    override val skillId = SkillId.JIANG_HU_LING

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        g.findEvent<SendCardEvent>(this) { event ->
            askWhom === event.sender
        } ?: return null
        return ResolveResult(executeJiangHuLingA(g.fsm!!, askWhom), true)
    }

    private data class executeJiangHuLingA(val fsm: Fsm, val r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (player in r.game!!.players) {
                if (player is HumanPlayer) {
                    val builder = skill_wait_for_jiang_hu_ling_a_toc.newBuilder()
                    builder.playerId = player.getAlternativeLocation(r.location)
                    builder.waitingSecond = Config.WaitSecond
                    if (player === r) {
                        val seq = player.seq
                        builder.seq = seq
                        player.timeout = GameExecutor.post(
                            player.game!!,
                            {
                                if (player.checkSeq(seq)) {
                                    val builder2 = skill_jiang_hu_ling_a_tos.newBuilder()
                                    builder2.enable = false
                                    builder2.seq = seq
                                    player.game!!.tryContinueResolveProtocol(player, builder2.build())
                                }
                            },
                            player.getWaitSeconds(builder.waitingSecond + 2).toLong(),
                            TimeUnit.SECONDS
                        )
                    }
                    player.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(r.game!!, {
                    val colors =
                        if (r.identity == Black) listOf(Black, Red, Blue)
                        else listOf(Black, Red, Blue) - r.identity
                    val color = colors.random()
                    val builder = skill_jiang_hu_ling_a_tos.newBuilder()
                    builder.enable = true
                    builder.color = color
                    r.game!!.tryContinueResolveProtocol(r, builder.build())
                }, 3, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                logger.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_jiang_hu_ling_a_tos) {
                logger.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
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
            if (message.color != Black && message.color != Red && message.color != Blue) {
                logger.error("未知的颜色类型")
                (player as? HumanPlayer)?.sendErrorMessage("未知的颜色类型")
                return null
            }
            r.incrSeq()
            r.skills += JiangHuLing2(message.color)
            logger.info("${r}发动了[江湖令]，宣言了${message.color}")
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jiang_hu_ling_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.color = message.color
                    p.send(builder.build())
                }
            }
            return ResolveResult(fsm, true)
        }
    }

    private class JiangHuLing2(val color: color) : TriggeredSkill, OneTurnSkill {
        override val skillId = SkillId.UNKNOWN

        override val isInitialSkill = false

        override fun execute(g: Game, askWhom: Player): ResolveResult? {
            val event = g.findEvent<ReceiveCardEvent>(this) { event ->
                askWhom === event.sender || return@findEvent false
                askWhom.alive
            } ?: return null
            if (!event.inFrontOfWhom.messageCards.any { color in it.colors }) {
                for (p in askWhom.game!!.players) {
                    if (p is HumanPlayer) {
                        val builder = skill_jiang_hu_ling_b_toc.newBuilder()
                        builder.playerId = p.getAlternativeLocation(askWhom.location)
                        builder.enable = false
                        p.send(builder.build())
                    }
                }
                return null
            }
            return ResolveResult(executeJiangHuLingB(g.fsm!!, event, color), true)
        }
    }

    private data class executeJiangHuLingB(val fsm: Fsm, val event: ReceiveCardEvent, val color: color) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in event.sender.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_wait_for_jiang_hu_ling_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(event.sender.location)
                    builder.color = color
                    builder.waitingSecond = Config.WaitSecond
                    if (p === event.sender) {
                        val seq = p.seq
                        builder.seq = seq
                        p.timeout = GameExecutor.post(
                            p.game!!,
                            {
                                if (p.checkSeq(seq)) {
                                    val builder2 = end_receive_phase_tos.newBuilder()
                                    builder2.seq = seq
                                    p.game!!.tryContinueResolveProtocol(p, builder2.build())
                                }
                            },
                            p.getWaitSeconds(builder.waitingSecond + 2).toLong(),
                            TimeUnit.SECONDS
                        )
                    }
                    p.send(builder.build())
                }
            }
            val p = event.sender
            if (p is RobotPlayer) {
                val target = event.inFrontOfWhom
                var value = 0
                var card: Card? = null
                for (c in target.messageCards.filter { color in it.colors }) {
                    var v = p.calculateRemoveCardValue(event.whoseTurn, target, c)
                    if (c.isBlack()) v += 10
                    if (v >= value) {
                        value = v
                        card = c
                    }
                }
                GameExecutor.post(p.game!!, {
                    if (card != null) {
                        val builder = skill_jiang_hu_ling_b_tos.newBuilder()
                        builder.cardId = card.id
                        p.game!!.tryContinueResolveProtocol(p, builder.build())
                    } else {
                        p.game!!.tryContinueResolveProtocol(p, end_receive_phase_tos.getDefaultInstance())
                    }
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== event.sender) {
                logger.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message is end_receive_phase_tos) {
                if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                    logger.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                    player.sendErrorMessage("操作太晚了")
                    return null
                }
                player.incrSeq()
                for (p in player.game!!.players) {
                    if (p is HumanPlayer) {
                        val builder = skill_jiang_hu_ling_b_toc.newBuilder()
                        builder.playerId = p.getAlternativeLocation(player.location)
                        builder.enable = false
                        p.send(builder.build())
                    }
                }
                return ResolveResult(fsm, true)
            }
            if (message !is skill_jiang_hu_ling_b_tos) {
                logger.error("错误的协议")
                return null
            }
            val r = event.sender
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            val target = event.inFrontOfWhom
            if (!target.alive) {
                logger.error("目标已死亡")
                (player as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                return null
            }
            val card = target.findMessageCard(message.cardId)
            if (card == null) {
                logger.error("没有这张卡")
                (player as? HumanPlayer)?.sendErrorMessage("没有这张卡")
                return null
            }
            if (color !in card.colors) {
                logger.error("你选择的情报不是宣言的颜色")
                (player as? HumanPlayer)?.sendErrorMessage("你选择的情报不是宣言的颜色")
                return null
            }
            r.incrSeq()
            logger.info("${r}发动了[江湖令]，弃掉了${target}面前的$card")
            target.deleteMessageCard(card.id)
            r.game!!.deck.discard(card)
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jiang_hu_ling_b_toc.newBuilder()
                    builder.cardId = card.id
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.enable = true
                    p.send(builder.build())
                }
            }
            if (card.colors.contains(Common.color.Black)) r.draw(1)
            return ResolveResult(fsm, true)
        }
    }
}