package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.protos.Common.card_type.*
import com.fengsheng.protos.Role.skill_qiang_ling_tos
import com.fengsheng.protos.skillQiangLingToc
import com.fengsheng.protos.skillQiangLingTos
import com.fengsheng.protos.skillWaitForQiangLingToc
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 张一挺技能【强令】：你传出情报后，或你决定接收情报后，可以宣言至多两个卡牌名称。本回合中，所有角色均不能使用被宣言的卡牌。
 */
class QiangLing : TriggeredSkill {
    override val skillId = SkillId.QIANG_LING

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event1 = g.findEvent<SendCardEvent>(this) { event ->
            askWhom === event.sender
        }
        if (event1 != null)
            return ResolveResult(ExecuteQiangLing(g.fsm!!, event1, askWhom), true)
        val event2 = g.findEvent<ChooseReceiveCardEvent>(this) { event ->
            askWhom === event.inFrontOfWhom
        }
        if (event2 != null) {
            return ResolveResult(ExecuteQiangLing(g.fsm!!, event2, askWhom), true)
        }
        return null
    }

    private data class ExecuteQiangLing(val fsm: Fsm, val event: Event, val r: Player) : WaitingFsm {
        override val whoseTurn: Player
            get() = fsm.whoseTurn

        override fun resolve(): ResolveResult? {
            r.game!!.players.send { player ->
                skillWaitForQiangLingToc {
                    playerId = player.getAlternativeLocation(r.location)
                    waitingSecond = Config.WaitSecond
                    if (player === r) {
                        val seq2 = player.seq
                        seq = seq2
                        player.timeout = GameExecutor.post(player.game!!, {
                            if (player.checkSeq(seq2))
                                player.game!!.tryContinueResolveProtocol(player, skillQiangLingTos { seq = seq2 })
                        }, player.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(r.game!!, {
                    if (event is ChooseReceiveCardEvent) {
                        val v = r.calculateMessageCardValue(event.whoseTurn, r, event.messageCard, sender = event.sender)
                        if (v < 0) { // 如果情报不值得接收，就不发动强令
                            r.game!!.tryContinueResolveProtocol(r, skillQiangLingTos { })
                            return@post
                        }
                    }
                    val result = listOf(Jie_Huo, Diao_Bao, Wu_Dao)
                        .filterNot { r.cannotPlayCard(it) }.run {
                            when (size) {
                                0 -> listOf(Po_Yi, Cheng_Qing).filterNot { r.cannotPlayCard(it) }
                                1 -> plus(if (event is SendCardEvent && !r.cannotPlayCard(Po_Yi)) Po_Yi else Cheng_Qing)
                                2 -> this
                                else -> sortedBy { type -> r.cards.any { it.type == type } }.take(2)
                            }
                        }
                    r.game!!.tryContinueResolveProtocol(r, skillQiangLingTos {
                        enable = result.isNotEmpty()
                        types.addAll(result)
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
            if (message !is skill_qiang_ling_tos) {
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
            if (message.typesCount == 0) {
                logger.error("enable为true时types不能为空")
                player.sendErrorMessage("[强令]的卡牌类型不能为空")
                return null
            }
            val typesList = message.typesList.toList()
            for (t in typesList) {
                if (t == UNRECOGNIZED || t == null) {
                    logger.error("未知的卡牌类型$t")
                    player.sendErrorMessage("未知的卡牌类型$t")
                    return null
                }
            }
            r.incrSeq()
            logger.info("${r}发动了[强令]，禁止了${typesList.joinToString()}")
            r.game!!.players.forEach { it!!.skills += CannotPlayCard(cardType = typesList) }
            r.game!!.players.send {
                skillQiangLingToc {
                    playerId = it.getAlternativeLocation(r.location)
                    types.addAll(typesList)
                }
            }
            return ResolveResult(fsm, true)
        }
    }
}
