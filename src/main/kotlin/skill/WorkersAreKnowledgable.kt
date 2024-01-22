package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.count
import com.fengsheng.card.countTrueCard
import com.fengsheng.phase.SendPhaseIdle
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Common.direction.Up
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 火车司机技能【咱们工人有知识】：根据你有的情报数量，
 * * 摸牌阶段：每张红/蓝情报，多摸一张牌
 * * 你传出非直达情报时：每张黑情报，可以选择一名角色本轮不能选择接收情报
 */
class WorkersAreKnowledgable : ChangeDrawCardCountSkill, TriggeredSkill {
    override val skillId = SkillId.WORKERS_ARE_KNOWLEDGABLE

    override val isInitialSkill = true

    override fun changeGameResult(player: Player, oldCount: Int) = oldCount + player.messageCards.countTrueCard()

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        g.findEvent<SendCardEvent>(this) { event ->
            askWhom === event.sender || return@findEvent false
            event.dir !== Up || return@findEvent false
            askWhom.messageCards.any { it.isBlack() }
        } ?: return null
        return ResolveResult(executeWorkersAreKnowledgable(g.fsm!!, askWhom), true)
    }

    private data class executeWorkersAreKnowledgable(val fsm: Fsm, val r: Player) :
        WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (player in r.game!!.players) {
                if (player is HumanPlayer) {
                    val builder = skill_wait_for_workers_are_knowledgable_toc.newBuilder()
                    builder.playerId = player.getAlternativeLocation(r.location)
                    builder.waitingSecond = Config.WaitSecond
                    if (player === r) {
                        val seq = player.seq
                        builder.seq = seq
                        GameExecutor.post(
                            player.game!!,
                            {
                                if (player.checkSeq(seq)) {
                                    val builder2 = skill_workers_are_knowledgable_tos.newBuilder()
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
                    val builder = skill_workers_are_knowledgable_tos.newBuilder()
                    val targets = r.game!!.players.filter { it !== r && it!!.alive }.shuffled()
                        .subList(0, r.messageCards.count(Black))
                    builder.enable = targets.isNotEmpty()
                    builder.addAllTargetPlayerId(targets.map { r.getAlternativeLocation(it!!.location) })
                    r.game!!.tryContinueResolveProtocol(r, builder.build())
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
            if (message !is skill_workers_are_knowledgable_tos) {
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
            if (message.targetPlayerIdCount == 0) {
                log.error("enable为true时至少要选择一个目标")
                (player as? HumanPlayer)?.sendErrorMessage("enable为true时至少要选择一个目标")
                return null
            }
            val maxCount = r.messageCards.count(Black)
            if (message.targetPlayerIdCount > maxCount) {
                log.error("最多选择${maxCount}个目标")
                (player as? HumanPlayer)?.sendErrorMessage("最多选择${maxCount}个目标")
                return null
            }
            val targets = message.targetPlayerIdList.map {
                if (it < 0 || it >= r.game!!.players.size) {
                    log.error("目标错误")
                    (player as? HumanPlayer)?.sendErrorMessage("目标错误")
                    return null
                }
                val target = r.game!!.players[r.getAbstractLocation(it)]!!
                if (!target.alive) {
                    log.error("目标已死亡")
                    (player as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                    return null
                }
                target
            }
            r.incrSeq()
            log.info("${r}发动了[咱们工人有知识]，令${targets.toTypedArray().contentToString()}本回合不能接收情报")
            targets.forEach { it.skills += WorkersAreKnowledgable2() }
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_workers_are_knowledgable_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.addAllTargetPlayerId(targets.map { p.getAlternativeLocation(it.location) })
                    p.send(builder.build())
                }
            }
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(executeWorkersAreKnowledgable::class.java)
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