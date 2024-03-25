package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.protos.*
import com.fengsheng.protos.Common.card_type.*
import com.fengsheng.protos.Role.skill_jiu_ji_a_tos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 李宁玉技能【就计】：你被【试探】【威逼】或【利诱】指定为目标后，你可以翻开此角色牌，然后摸两张牌，并在触发此技能的卡牌结算后，将其加入你的手牌。
 */
class JiuJi : TriggeredSkill {
    override val skillId = SkillId.JIU_JI

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<UseCardEvent>(this) { event ->
            askWhom === event.targetPlayer || return@findEvent false
            askWhom.alive || return@findEvent false
            event.cardType in cardTypes || return@findEvent false
            !askWhom.roleFaceUp
        } ?: return null
        return ResolveResult(ExecuteJiuJi(g.fsm!!, event, askWhom), true)
    }

    private data class ExecuteJiuJi(val fsm: Fsm, val event: UseCardEvent, val r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            r.game!!.players.send { player ->
                if (player === r) skillWaitForJiuJiToc {
                    fromPlayerId = player.getAlternativeLocation(event.player.location)
                    cardType = event.cardType
                    if (event.cardType != Shi_Tan) event.card?.let { card = it.toPbCard() }
                    waitingSecond = Config.WaitSecond
                    val seq2 = player.seq
                    seq = seq2
                    player.timeout = GameExecutor.post(r.game!!, {
                        if (r.checkSeq(seq2)) {
                            r.game!!.tryContinueResolveProtocol(r, skillJiuJiATos {
                                enable = true
                                seq = seq2
                            })
                        }
                    }, player.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                }
                else unknownWaitingToc { waitingSecond = Config.WaitSecond }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(r.game!!, {
                    r.game!!.tryContinueResolveProtocol(r, skillJiuJiATos { enable = event.card != null })
                }, 100, TimeUnit.MILLISECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== r) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_jiu_ji_a_tos) {
                logger.error("错误的协议")
                player.sendErrorMessage("错误的协议")
                return null
            }
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            if (!message.enable) {
                r.incrSeq()
                return ResolveResult(fsm, true)
            }
            r.incrSeq()
            g.playerSetRoleFaceUp(r, true)
            logger.info("${r}发动了[就计]")
            g.players.send { skillJiuJiAToc { playerId = it.getAlternativeLocation(r.location) } }
            r.draw(2)
            event.card?.let { r.skills += JiuJi2() }
            return ResolveResult(fsm, true)
        }
    }

    private class JiuJi2 : TriggeredSkill {
        override val skillId = SkillId.UNKNOWN

        override val isInitialSkill = false

        override fun execute(g: Game, askWhom: Player): ResolveResult? {
            val event = g.findEvent<FinishResolveCardEvent>(this) { event ->
                askWhom === event.targetPlayer || return@findEvent false
                askWhom.alive || return@findEvent false
                event.card != null
            } ?: return null
            val card = event.card!!
            askWhom.cards.add(card)
            logger.info("${askWhom}将使用的${card}加入了手牌")
            askWhom.skills = askWhom.skills.filterNot { it === this }
            g.players.send {
                skillJiuJiBToc {
                    playerId = it.getAlternativeLocation(askWhom.location)
                    this.card = card.toPbCard()
                }
            }
            event.discardAfterResolve = false
            return null
        }
    }

    companion object {
        private val cardTypes = listOf(Shi_Tan, Wei_Bi, Li_You)
    }
}
