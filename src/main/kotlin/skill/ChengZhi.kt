package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.count
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Common.secret_task
import com.fengsheng.protos.Common.secret_task.*
import com.fengsheng.protos.Role.skill_cheng_zhi_tos
import com.fengsheng.protos.skillChengZhiToc
import com.fengsheng.protos.skillChengZhiTos
import com.fengsheng.protos.skillWaitForChengZhiToc
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 顾小梦技能【承志】：一名其他角色死亡前，若此角色牌已翻开，则你获得其所有手牌，并查看其身份牌，你可以获得该身份牌，并将你原本的身份牌面朝下移出游戏。
 */
class ChengZhi : TriggeredSkill {
    override val skillId = SkillId.CHENG_ZHI

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<PlayerDieEvent>(this) { event ->
            askWhom !== event.whoDie || return@findEvent false
            askWhom.alive || return@findEvent false
            askWhom.roleFaceUp
        } ?: return null
        val whoDie = event.whoDie
        return ResolveResult(
            ExecuteChengZhi(g.fsm!!, askWhom, whoDie, whoDie.cards.isNotEmpty()),
            true
        )
    }

    private data class ExecuteChengZhi(
        val fsm: Fsm,
        val r: Player,
        val whoDie: Player,
        val hasCard: Boolean
    ) : WaitingFsm {
        override val whoseTurn: Player
            get() = fsm.whoseTurn

        override fun resolve(): ResolveResult? {
            val cards = whoDie.cards.toList()
            whoDie.cards.clear()
            r.cards.addAll(cards)
            if (hasCard) {
                logger.info("${r}发动了[承志]，获得了${whoDie}的${cards.joinToString()}并查看身份牌")
                r.game!!.addEvent(GiveCardEvent(whoseTurn, whoDie, r))
            } else {
                logger.info("${r}发动了[承志]，查看了${whoDie}的身份牌")
            }
            if (whoDie.identity == Has_No_Identity) return ResolveResult(fsm, true)
            r.game!!.players.send { player ->
                skillWaitForChengZhiToc {
                    playerId = player.getAlternativeLocation(r.location)
                    waitingSecond = Config.WaitSecond
                    diePlayerId = player.getAlternativeLocation(whoDie.location)
                    if (player === r) {
                        cards.forEach { this.cards.add(it.toPbCard()) }
                        identity = whoDie.identity
                        secretTask = whoDie.secretTask
                        val seq2 = player.seq
                        seq = seq2
                        player.timeout = GameExecutor.post(r.game!!, {
                            if (r.checkSeq(seq2)) {
                                r.game!!.tryContinueResolveProtocol(r, skillChengZhiTos {
                                    enable = false
                                    seq = seq2
                                })
                            }
                        }, player.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                }
            }
            if (r is RobotPlayer) GameExecutor.post(r.game!!, {
                fun Player.process(identity: color, secretTask: secret_task) = if (identity != Black) {
                    maxOf(game!!.players.filter { it!!.alive && it.identity == identity }
                        .maxOfOrNull { it!!.messageCards.count(identity) * 10 } ?: 0,
                        r.messageCards.count(identity) * 10
                    )
                } else when (secretTask) {
                    Collector -> maxOf(messageCards.count(Red), messageCards.count(Blue)) * 2 - 3
                    Mutator -> game!!.players.filter { it!!.alive }
                        .maxOf { maxOf(it!!.messageCards.count(Red), it.messageCards.count(Blue)) * 2 - 2 }

                    Pioneer -> messageCards.count(Black) * 10 - 1
                    else -> -100
                }
                r.game!!.tryContinueResolveProtocol(r, skillChengZhiTos {
                    enable = whoDie.identity == r.identity && whoDie.secretTask == r.secretTask ||
                        r.process(whoDie.identity, whoDie.secretTask) > r.process(r.identity, r.secretTask)
                })
            }, 3, TimeUnit.SECONDS)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== r) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_cheng_zhi_tos) {
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
                g.players.send {
                    skillChengZhiToc {
                        enable = false
                        playerId = it.getAlternativeLocation(r.location)
                        diePlayerId = it.getAlternativeLocation(whoDie.location)
                    }
                }
                return ResolveResult(fsm, true)
            }
            r.incrSeq()
            r.identity = whoDie.identity
            r.secretTask = whoDie.secretTask
            whoDie.identity = Has_No_Identity
            logger.info("${r}获得了${whoDie}的身份牌")
            g.players.send {
                skillChengZhiToc {
                    enable = true
                    playerId = it.getAlternativeLocation(r.location)
                    diePlayerId = it.getAlternativeLocation(whoDie.location)
                }
            }
            return ResolveResult(fsm, true)
        }
    }
}
