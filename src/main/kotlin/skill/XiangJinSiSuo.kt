package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.ReceivePhaseIdle
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 孙守謨技能【详尽思索】：每当情报传出时，你可以指定一名角色，若最后情报被该角色接收，你摸两张牌。
 */
class XiangJinSiSuo : TriggeredSkill {
    override val skillId = SkillId.XIANG_JIN_SI_SUO

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<SendCardEvent>(this) { true } ?: return null
        return ResolveResult(executeXiangJinSiSuoA(g.fsm!!, event, askWhom), true)
    }

    private data class executeXiangJinSiSuoA(val fsm: Fsm, val event: SendCardEvent, val r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (player in r.game!!.players) {
                if (player is HumanPlayer) {
                    val builder = skill_wait_for_xiang_jin_si_suo_toc.newBuilder()
                    builder.playerId = player.getAlternativeLocation(r.location)
                    builder.waitingSecond = Config.WaitSecond
                    if (player === r) {
                        val seq = player.seq
                        builder.seq = seq
                        player.timeout = GameExecutor.post(
                            player.game!!,
                            {
                                if (player.checkSeq(seq)) {
                                    val builder2 = skill_xiang_jin_si_suo_a_tos.newBuilder()
                                    builder2.enable = true
                                    builder2.targetPlayerId = r.getAlternativeLocation(event.targetPlayer.location)
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
                    val builder = skill_xiang_jin_si_suo_a_tos.newBuilder()
                    r.game!!.players.filter { it!!.alive }.randomOrNull()?.let {
                        builder.enable = true
                        builder.targetPlayerId = r.getAlternativeLocation(event.targetPlayer.location)
                    }
                    r.game!!.tryContinueResolveProtocol(r, builder.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                logger.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_xiang_jin_si_suo_a_tos) {
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
                for (p in r.game!!.players) {
                    if (p is HumanPlayer) {
                        val builder = skill_xiang_jin_si_suo_a_toc.newBuilder()
                        builder.playerId = p.getAlternativeLocation(r.location)
                        builder.enable = false
                        p.send(builder.build())
                    }
                }
                return ResolveResult(fsm, true)
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= r.game!!.players.size) {
                logger.error("目标错误")
                (player as? HumanPlayer)?.sendErrorMessage("目标错误")
                return null
            }
            val target = r.game!!.players[r.getAbstractLocation(message.targetPlayerId)]!!
            if (!target.alive) {
                logger.error("目标已死亡")
                (player as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                return null
            }
            r.incrSeq()
            r.skills += XiangJinSiSuo2(target)
            logger.info("${r}发动了[详尽思索]，指定了${target}")
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_xiang_jin_si_suo_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.enable = true
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    p.send(builder.build())
                }
            }
            return ResolveResult(fsm, true)
        }

        companion object {
        }
    }

    private class XiangJinSiSuo2(val target: Player) : TriggeredSkill, OneTurnSkill {
        override val skillId = SkillId.UNKNOWN

        override val isInitialSkill = false

        override fun execute(g: Game, askWhom: Player): ResolveResult? {
            val fsm = g.fsm as? ReceivePhaseIdle ?: return null
            target === fsm.inFrontOfWhom || return null
            askWhom.alive || return null
            askWhom.getSkillUseCount(skillId) == 0 || return null
            askWhom.addSkillUseCount(skillId)
            logger.info("[详尽思索]命中")
            for (p in askWhom.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_xiang_jin_si_suo_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(askWhom.location)
                    p.send(builder.build())
                }
            }
            askWhom.draw(1)
            return ResolveResult(fsm, true)
        }

        companion object {
        }
    }
}