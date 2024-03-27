package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.count
import com.fengsheng.card.countTrueCard
import com.fengsheng.phase.SendPhaseIdle
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Common.direction.Up
import com.fengsheng.protos.Role.skill_workers_are_knowledgable_tos
import com.fengsheng.protos.skillWaitForWorkersAreKnowledgableToc
import com.fengsheng.protos.skillWorkersAreKnowledgableToc
import com.fengsheng.protos.skillWorkersAreKnowledgableTos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 火车司机技能【咱们工人有知识】：根据你有的情报数量，
 * * 摸牌阶段：每张红/蓝情报，多摸一张牌
 * * 你传出非直达情报时：每张黑情报，可以选择一名角色本轮不能选择接收情报
 */
class WorkersAreKnowledgable : ChangeDrawCardCountSkill, TriggeredSkill {
    override val skillId = SkillId.WORKERS_ARE_KNOWLEDGABLE

    override val isInitialSkill = true

    override fun changeDrawCardCount(player: Player, oldCount: Int) = oldCount + player.messageCards.countTrueCard()

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        g.findEvent<SendCardEvent>(this) { event ->
            askWhom === event.sender || return@findEvent false
            event.dir !== Up || return@findEvent false
            askWhom.messageCards.any { it.isBlack() }
        } ?: return null
        return ResolveResult(ExecuteWorkersAreKnowledgable(g.fsm!!, askWhom), true)
    }

    private data class ExecuteWorkersAreKnowledgable(val fsm: Fsm, val r: Player) : WaitingFsm {
        override val whoseTurn: Player
            get() = fsm.whoseTurn

        override fun resolve(): ResolveResult? {
            r.game!!.players.send { player ->
                skillWaitForWorkersAreKnowledgableToc {
                    playerId = player.getAlternativeLocation(r.location)
                    waitingSecond = Config.WaitSecond
                    if (player === r) {
                        val seq = player.seq
                        this.seq = seq
                        player.timeout = GameExecutor.post(player.game!!, {
                            if (player.checkSeq(seq)) {
                                player.game!!.tryContinueResolveProtocol(player, skillWorkersAreKnowledgableTos {
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
                    val targets = r.game!!.players.filter { it!!.alive && it.isEnemy(r) }.shuffled()
                        .take(r.messageCards.count(Black))
                    r.game!!.tryContinueResolveProtocol(r, skillWorkersAreKnowledgableTos {
                        enable = targets.isNotEmpty()
                        targets.forEach { targetPlayerId.add(r.getAlternativeLocation(it!!.location)) }
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
            if (message !is skill_workers_are_knowledgable_tos) {
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
            if (message.targetPlayerIdCount == 0) {
                logger.error("enable为true时至少要选择一个目标")
                player.sendErrorMessage("enable为true时至少要选择一个目标")
                return null
            }
            val maxCount = r.messageCards.count(Black)
            if (message.targetPlayerIdCount > maxCount) {
                logger.error("最多选择${maxCount}个目标")
                player.sendErrorMessage("最多选择${maxCount}个目标")
                return null
            }
            val targets = message.targetPlayerIdList.map {
                if (it < 0 || it >= r.game!!.players.size) {
                    logger.error("目标错误")
                    player.sendErrorMessage("目标错误")
                    return null
                }
                val target = r.game!!.players[r.getAbstractLocation(it)]!!
                if (!target.alive) {
                    logger.error("目标已死亡")
                    player.sendErrorMessage("目标已死亡")
                    return null
                }
                target
            }
            r.incrSeq()
            logger.info("${r}发动了[咱们工人有知识]，令${targets.joinToString()}本回合不能接收情报")
            targets.forEach { it.skills += WorkersAreKnowledgable2() }
            r.game!!.players.send { p ->
                skillWorkersAreKnowledgableToc {
                    playerId = p.getAlternativeLocation(r.location)
                    targets.forEach { targetPlayerId.add(p.getAlternativeLocation(it.location)) }
                }
            }
            return ResolveResult(fsm, true)
        }
    }

    /**
     * 有这个技能的角色本回合不能接收情报
     */
    private class WorkersAreKnowledgable2 : MustReceiveMessage() {
        override val isInitialSkill = false

        override fun mustReceive(sendPhase: SendPhaseIdle) = false

        override fun cannotReceive(sendPhase: SendPhaseIdle) = true
    }
}
