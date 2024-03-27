package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.protos.*
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Fengsheng.end_receive_phase_tos
import com.fengsheng.protos.Role.skill_jiang_hu_ling_a_tos
import com.fengsheng.protos.Role.skill_jiang_hu_ling_b_tos
import com.google.protobuf.GeneratedMessage
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
        return ResolveResult(ExecuteJiangHuLingA(g.fsm!!, askWhom), true)
    }

    private data class ExecuteJiangHuLingA(val fsm: Fsm, val r: Player) : WaitingFsm {
        override val whoseTurn: Player
            get() = fsm.whoseTurn

        override fun resolve(): ResolveResult? {
            r.game!!.players.send { player ->
                skillWaitForJiangHuLingAToc {
                    playerId = player.getAlternativeLocation(r.location)
                    waitingSecond = Config.WaitSecond
                    if (player === r) {
                        val seq = player.seq
                        this.seq = seq
                        player.timeout = GameExecutor.post(player.game!!, {
                            if (player.checkSeq(seq)) {
                                player.game!!.tryContinueResolveProtocol(player, skillJiangHuLingATos {
                                    enable = false
                                    this.seq = seq
                                })
                            }
                        }, player.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(r.game!!, {
                    val color =
                        if (r.identity == Black) Black
                        else (listOf(Black, Red, Blue) - r.identity).random()
                    r.game!!.tryContinueResolveProtocol(r, skillJiangHuLingATos {
                        enable = true
                        this.color = color
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
            if (message !is skill_jiang_hu_ling_a_tos) {
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
            if (message.color != Black && message.color != Red && message.color != Blue) {
                logger.error("未知的颜色类型")
                player.sendErrorMessage("未知的颜色类型")
                return null
            }
            r.incrSeq()
            r.skills += JiangHuLing2(message.color)
            logger.info("${r}发动了[江湖令]，宣言了${message.color}")
            r.game!!.players.send {
                skillJiangHuLingAToc {
                    playerId = it.getAlternativeLocation(r.location)
                    color = message.color
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
                askWhom.game!!.players.send {
                    skillJiangHuLingBToc {
                        playerId = it.getAlternativeLocation(askWhom.location)
                        enable = false
                    }
                }
                return null
            }
            return ResolveResult(ExecuteJiangHuLingB(g.fsm!!, event, color), true)
        }
    }

    private data class ExecuteJiangHuLingB(val fsm: Fsm, val event: ReceiveCardEvent, val color: color) : WaitingFsm {
        override val whoseTurn: Player
            get() = fsm.whoseTurn

        override fun resolve(): ResolveResult? {
            event.sender.game!!.players.send { p ->
                skillWaitForJiangHuLingBToc {
                    playerId = p.getAlternativeLocation(event.sender.location)
                    color = this@ExecuteJiangHuLingB.color
                    waitingSecond = Config.WaitSecond
                    if (p === event.sender) {
                        val seq = p.seq
                        this.seq = seq
                        p.timeout = GameExecutor.post(p.game!!, {
                            if (p.checkSeq(seq))
                                p.game!!.tryContinueResolveProtocol(p, endReceivePhaseTos { this.seq = seq })
                        }, p.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
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
                if (card != null) {
                    GameExecutor.post(p.game!!, {
                        p.game!!.tryContinueResolveProtocol(p, skillJiangHuLingBTos { cardId = card.id })
                    }, 3, TimeUnit.SECONDS)
                } else {
                    GameExecutor.post(p.game!!, {
                        p.game!!.tryContinueResolveProtocol(p, endReceivePhaseTos {})
                    }, 500, TimeUnit.MILLISECONDS)
                }
            }
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
                player.game!!.players.send {
                    skillJiangHuLingBToc {
                        playerId = it.getAlternativeLocation(player.location)
                        enable = false
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
                player.sendErrorMessage("目标已死亡")
                return null
            }
            val card = target.findMessageCard(message.cardId)
            if (card == null) {
                logger.error("没有这张卡")
                player.sendErrorMessage("没有这张卡")
                return null
            }
            if (color !in card.colors) {
                logger.error("你选择的情报不是宣言的颜色")
                player.sendErrorMessage("你选择的情报不是宣言的颜色")
                return null
            }
            r.incrSeq()
            logger.info("${r}发动了[江湖令]，弃掉了${target}面前的$card")
            target.deleteMessageCard(card.id)
            r.game!!.deck.discard(card)
            r.game!!.players.send {
                skillJiangHuLingBToc {
                    cardId = card.id
                    playerId = it.getAlternativeLocation(r.location)
                    enable = true
                }
            }
            if (card.colors.contains(Common.color.Black)) r.draw(1)
            return ResolveResult(fsm, true)
        }
    }
}
