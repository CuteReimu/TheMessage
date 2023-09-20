package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common.card_type.*
import com.fengsheng.protos.Fengsheng
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 李宁玉技能【就计】：你被【试探】【威逼】或【利诱】指定为目标后，你可以翻开此角色牌，然后摸两张牌，并在触发此技能的卡牌结算后，将其加入你的手牌。
 */
class JiuJi : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.JIU_JI

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val fsm = g.fsm as? OnUseCard ?: return null
        askWhom === fsm.targetPlayer || return null
        askWhom.alive || return null
        askWhom.findSkill(skillId) != null || return null
        fsm.cardType in cardTypes || return null
        !askWhom.roleFaceUp || return null
        askWhom.getSkillUseCount(skillId) == 0 || return null
        askWhom.addSkillUseCount(skillId)
        val oldResolveFunc = fsm.resolveFunc
        return ResolveResult(executeJiuJi(fsm.copy(resolveFunc = { valid: Boolean ->
            askWhom.resetSkillUseCount(skillId)
            oldResolveFunc(valid)
        }), askWhom), true)
    }

    private data class executeJiuJi(val fsm: OnUseCard, val r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (player in r.game!!.players) {
                if (player is HumanPlayer) {
                    if (player === r) {
                        val builder = skill_wait_for_jiu_ji_toc.newBuilder()
                        builder.fromPlayerId = player.getAlternativeLocation(fsm.player.location)
                        builder.cardType = fsm.cardType
                        fsm.card?.let { builder.card = it.toPbCard() }
                        builder.waitingSecond = Config.WaitSecond
                        val seq2 = player.seq
                        builder.seq = seq2
                        player.timeout = GameExecutor.post(r.game!!, {
                            val builder2 = skill_jiu_ji_a_tos.newBuilder()
                            builder2.enable = true
                            builder2.seq = seq2
                            r.game!!.tryContinueResolveProtocol(r, builder2.build())
                        }, player.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                        player.send(builder.build())
                    } else {
                        val builder = Fengsheng.unknown_waiting_toc.newBuilder()
                        builder.waitingSecond = Config.WaitSecond
                        player.send(builder.build())
                    }
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(r.game!!, {
                    val builder2 = skill_jiu_ji_a_tos.newBuilder()
                    builder2.enable = true
                    r.game!!.tryContinueResolveProtocol(r, builder2.build())
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
            if (message !is skill_jiu_ji_a_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            if (!message.enable) {
                r.incrSeq()
                return ResolveResult(fsm, true)
            }
            r.incrSeq()
            log.info("${r}发动了[就计]")
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jiu_ji_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    p.send(builder.build())
                }
            }
            g.playerSetRoleFaceUp(r, true)
            r.draw(2)
            fsm.card?.let {
                val skill = JiuJi2()
                r.skills = arrayOf(*r.skills, skill)
            }
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(executeJiuJi::class.java)
        }
    }

    private class JiuJi2 : TriggeredSkill {
        override val skillId = SkillId.JIU_JI2

        override fun execute(g: Game, askWhom: Player): ResolveResult? {
            val fsm = g.fsm as? OnFinishResolveCard ?: return null
            askWhom === fsm.targetPlayer || return null
            askWhom.alive || return null
            askWhom.findSkill(skillId) != null || return null
            val card = fsm.card ?: return null
            askWhom.cards.add(card)
            log.info("${askWhom}将使用的${card}加入了手牌")
            askWhom.skills = askWhom.skills.filterNot { it.skillId == skillId }.toTypedArray()
            for (player in g.players) {
                if (player is HumanPlayer) {
                    val builder = skill_jiu_ji_b_toc.newBuilder()
                    builder.playerId = player.getAlternativeLocation(askWhom.location)
                    builder.card = card.toPbCard()
                    player.send(builder.build())
                }
            }
            return ResolveResult(fsm.copy(discardAfterResolve = false), true)
        }

        companion object {
            private val log = Logger.getLogger(JiuJi::class.java)
        }
    }

    companion object {
        private val cardTypes = listOf(Shi_Tan, Wei_Bi, Li_You)
    }
}