package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.ReceivePhaseIdle
import com.fengsheng.protos.Common.card_type.Jie_Huo
import com.fengsheng.protos.Common.card_type.Wu_Dao
import com.fengsheng.protos.Common.direction.Up
import com.fengsheng.protos.Role.skill_xiang_jin_si_suo_a_tos
import com.fengsheng.protos.skillWaitForXiangJinSiSuoToc
import com.fengsheng.protos.skillXiangJinSiSuoAToc
import com.fengsheng.protos.skillXiangJinSiSuoATos
import com.fengsheng.protos.skillXiangJinSiSuoBToc
import com.google.protobuf.GeneratedMessage
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
        return ResolveResult(ExecuteXiangJinSiSuoA(g.fsm!!, event, askWhom), true)
    }

    private data class ExecuteXiangJinSiSuoA(val fsm: Fsm, val event: SendCardEvent, val r: Player) : WaitingFsm {
        override val whoseTurn: Player
            get() = fsm.whoseTurn

        override fun resolve(): ResolveResult? {
            r.game!!.players.send { player ->
                skillWaitForXiangJinSiSuoToc {
                    playerId = player.getAlternativeLocation(r.location)
                    waitingSecond = Config.WaitSecond / 2
                    if (player === r) {
                        val seq = player.seq
                        this.seq = seq
                        player.timeout = GameExecutor.post(player.game!!, {
                            if (player.checkSeq(seq)) {
                                player.game!!.tryContinueResolveProtocol(player, skillXiangJinSiSuoATos {
                                    enable = true
                                    targetPlayerId = r.getAlternativeLocation(event.targetPlayer.location)
                                    this.seq = seq
                                })
                            }
                        }, player.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(r.game!!, {
                    r.game!!.tryContinueResolveProtocol(r, skillXiangJinSiSuoATos {
                        val whoseTurn = event.whoseTurn
                        val sender = event.sender
                        val target = event.targetPlayer
                        val availablePlayers = arrayListOf(target)
                        if (event.dir == Up && !event.lockedPlayers.any { it === target }) {
                            val v1 = target.calculateMessageCardValue(whoseTurn, target, event.messageCard, sender = sender)
                            val v2 = target.calculateMessageCardValue(whoseTurn, sender, event.messageCard, sender = sender)
                            if (v1 < v2)
                                availablePlayers[0] = sender
                        }
                        if (!r.cannotPlayCard(Jie_Huo) && r.cards.any { r.canUseCardTypes(Jie_Huo, it).first }) {
                            availablePlayers.add(r)
                        }
                        if (!r.cannotPlayCard(Wu_Dao) && r.cards.any { r.canUseCardTypes(Wu_Dao, it).first }) {
                            availablePlayers.add(availablePlayers[0].getNextLeftAlivePlayer())
                            availablePlayers.add(availablePlayers[0].getNextRightAlivePlayer())
                        }
                        var v = Int.MIN_VALUE
                        var bestTarget = availablePlayers[0]
                        for (t in availablePlayers) {
                            val v1 = r.calculateMessageCardValue(whoseTurn, t, event.messageCard, sender = sender)
                            if (v1 > v) {
                                v = v1
                                bestTarget = t
                            }
                        }
                        enable = true
                        targetPlayerId = r.getAlternativeLocation(bestTarget.location)
                    })
                }, 1, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== r) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_xiang_jin_si_suo_a_tos) {
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
                r.game!!.players.send {
                    skillXiangJinSiSuoAToc {
                        playerId = it.getAlternativeLocation(r.location)
                        enable = false
                    }
                }
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
            r.skills += XiangJinSiSuo2(target)
            logger.info("${r}发动了[详尽思索]，指定了$target")
            r.game!!.players.send {
                skillXiangJinSiSuoAToc {
                    playerId = it.getAlternativeLocation(r.location)
                    enable = true
                    targetPlayerId = it.getAlternativeLocation(target.location)
                }
            }
            return ResolveResult(fsm, true)
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
            askWhom.game!!.players.send {
                skillXiangJinSiSuoBToc { playerId = it.getAlternativeLocation(askWhom.location) }
            }
            askWhom.draw(1)
            return ResolveResult(fsm, true)
        }
    }
}
