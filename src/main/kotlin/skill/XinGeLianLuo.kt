package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.SendPhaseIdle
import com.fengsheng.protos.Common.direction.Up
import com.fengsheng.protos.Role.skill_xin_ge_lian_luo_tos
import com.fengsheng.protos.skillWaitForXinGeLianLuoToc
import com.fengsheng.protos.skillXinGeLianLuoToc
import com.fengsheng.protos.skillXinGeLianLuoTos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 小铃铛技能【信鸽联络】：每当你传出非直达情报时，可以选择一名角色本轮的传递阶段中不能选择接收情报。
 */
class XinGeLianLuo : TriggeredSkill {
    override val skillId = SkillId.XIN_GE_LIAN_LUO

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        g.findEvent<SendCardEvent>(this) { event ->
            askWhom === event.sender && event.dir !== Up
        } ?: return null
        return ResolveResult(ExecuteXinGeLianLuo(g.fsm!!, askWhom), true)
    }

    private data class ExecuteXinGeLianLuo(val fsm: Fsm, val r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            r.game!!.players.send { player ->
                skillWaitForXinGeLianLuoToc {
                    playerId = player.getAlternativeLocation(r.location)
                    waitingSecond = Config.WaitSecond
                    if (player === r) {
                        val seq = player.seq
                        this.seq = seq
                        player.timeout = GameExecutor.post(player.game!!, {
                            if (player.checkSeq(seq)) {
                                player.game!!.tryContinueResolveProtocol(player, skillXinGeLianLuoTos {
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
                    val target = r.game!!.players.filter { it!!.alive && it.isEnemy(r) }.randomOrNull()
                    r.game!!.tryContinueResolveProtocol(r, skillXinGeLianLuoTos {
                        target?.let {
                            enable = true
                            targetPlayerId = r.getAlternativeLocation(it.location)
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
            if (message !is skill_xin_ge_lian_luo_tos) {
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
            if (message.targetPlayerId < 0 || message.targetPlayerId >= r.game!!.players.size) {
                logger.error("目标错误")
                player.sendErrorMessage("目标错误")
                return null
            }
            val target = r.game!!.players[r.getAbstractLocation(message.targetPlayerId)]!!
            if (!target.alive) {
                logger.error("目标已死亡")
                player.sendErrorMessage("目标已死亡")
                return null
            }
            r.incrSeq()
            logger.info("${r}发动了[信鸽联络]，令${target}本回合不能接收情报")
            target.skills += XinGeLianLuo2()
            r.game!!.players.send {
                skillXinGeLianLuoToc {
                    playerId = it.getAlternativeLocation(r.location)
                    targetPlayerId = it.getAlternativeLocation(target.location)
                }
            }
            return ResolveResult(fsm, true)
        }
    }

    /**
     * 有这个技能的角色本回合不能接收情报
     */
    private class XinGeLianLuo2 : MustReceiveMessage() {
        override val isInitialSkill = false

        override fun mustReceive(sendPhase: SendPhaseIdle) = false

        override fun cannotReceive(sendPhase: SendPhaseIdle) = true
    }
}
