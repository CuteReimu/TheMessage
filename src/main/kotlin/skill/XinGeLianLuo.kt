package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.SendPhaseIdle
import com.fengsheng.protos.Common.direction.Up
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 小铃铛技能【信鸽联络】：每当你传出非直达情报时，可以选择一名角色本轮的传递阶段中不能选择接收情报。
 */
class XinGeLianLuo : InitialSkill, TriggeredSkill {
    override val skillId = SkillId.XIN_GE_LIAN_LUO

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        g.findEvent<SendCardEvent>(this) { event ->
            askWhom === event.sender && event.dir !== Up
        } ?: return null
        return ResolveResult(executeXinGeLianLuo(g.fsm!!, askWhom), true)
    }

    private data class executeXinGeLianLuo(val fsm: Fsm, val r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (player in r.game!!.players) {
                if (player is HumanPlayer) {
                    val builder = skill_wait_for_xin_ge_lian_luo_toc.newBuilder()
                    builder.playerId = player.getAlternativeLocation(r.location)
                    builder.waitingSecond = Config.WaitSecond
                    if (player === r) {
                        val seq = player.seq
                        builder.seq = seq
                        GameExecutor.post(
                            player.game!!,
                            {
                                if (player.checkSeq(seq)) {
                                    val builder2 = skill_xin_ge_lian_luo_tos.newBuilder()
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
                    val builder = skill_xin_ge_lian_luo_tos.newBuilder()
                    r.game!!.players.filter { it !== r && it!!.alive }.randomOrNull()?.let { target ->
                        builder.enable = true
                        builder.targetPlayerId = r.getAlternativeLocation(target.location)
                    }
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
            if (message !is skill_xin_ge_lian_luo_tos) {
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
            if (message.targetPlayerId < 0 || message.targetPlayerId >= r.game!!.players.size) {
                log.error("目标错误")
                (player as? HumanPlayer)?.sendErrorMessage("目标错误")
                return null
            }
            val target = r.game!!.players[r.getAbstractLocation(message.targetPlayerId)]!!
            if (!target.alive) {
                log.error("目标已死亡")
                (player as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                return null
            }
            r.incrSeq()
            log.info("${r}发动了[信鸽联络]，令${target}本回合不能接收情报")
            target.skills += XinGeLianLuo2()
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_xin_ge_lian_luo_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    p.send(builder.build())
                }
            }
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(executeXinGeLianLuo::class.java)
        }
    }

    /**
     * 有这个技能的角色本回合不能接收情报
     */
    private class XinGeLianLuo2 : MustReceiveMessage() {
        override fun mustReceive(sendPhase: SendPhaseIdle) = false

        override fun cannotReceive(sendPhase: SendPhaseIdle) = true
    }
}