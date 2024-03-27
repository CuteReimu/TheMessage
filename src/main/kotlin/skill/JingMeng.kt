package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.bestCard
import com.fengsheng.protos.Fengsheng.end_receive_phase_tos
import com.fengsheng.protos.Role.skill_jing_meng_a_tos
import com.fengsheng.protos.Role.skill_jing_meng_b_tos
import com.fengsheng.protos.skillJingMengAToc
import com.fengsheng.protos.skillJingMengATos
import com.fengsheng.protos.skillJingMengBToc
import com.fengsheng.protos.skillJingMengBTos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 程小蝶技能【惊梦】：你接收黑色情报后，可以查看一名角色的手牌。然后弃置其中一张牌，然后从中选择一张弃置。
 */
class JingMeng : TriggeredSkill {
    override val skillId = SkillId.JING_MENG

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<ReceiveCardEvent>(this) { event ->
            askWhom === event.inFrontOfWhom || return@findEvent false
            g.players.any { it!!.alive && it.cards.isNotEmpty() }
            event.messageCard.isBlack()
        } ?: return null
        return ResolveResult(ExecuteJingMengA(g.fsm!!, event), true)
    }

    private data class ExecuteJingMengA(val fsm: Fsm, val event: ReceiveCardEvent) : WaitingFsm {
        override val whoseTurn: Player
            get() = fsm.whoseTurn

        override fun resolve(): ResolveResult? {
            for (p in event.whoseTurn.game!!.players)
                p!!.notifyReceivePhase(event.whoseTurn, event.inFrontOfWhom, event.messageCard, event.inFrontOfWhom)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== event.inFrontOfWhom) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message is end_receive_phase_tos) {
                if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                    logger.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                    player.sendErrorMessage("操作太晚了")
                    return null
                }
                player.incrSeq()
                return ResolveResult(fsm, true)
            }
            if (message !is skill_jing_meng_a_tos) {
                logger.error("错误的协议")
                player.sendErrorMessage("错误的协议")
                return null
            }
            val r = event.inFrontOfWhom
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                logger.error("目标错误：${message.targetPlayerId}")
                player.sendErrorMessage("目标错误：${message.targetPlayerId}")
                return null
            }
            val target = g.players[r.getAbstractLocation(message.targetPlayerId)]!!
            if (!target.alive) {
                logger.error("目标已死亡")
                player.sendErrorMessage("目标已死亡")
                return null
            }
            if (target.cards.isEmpty()) {
                logger.error("目标没有手牌")
                player.sendErrorMessage("目标没有手牌")
                return null
            }
            r.incrSeq()
            logger.info("${r}发动了[惊梦]，查看了${target}的手牌")
            r.weiBiFailRate = 0
            return ResolveResult(ExecuteJingMengB(fsm, event, target), true)
        }
    }

    private data class ExecuteJingMengB(val fsm: Fsm, val event: ReceiveCardEvent, val target: Player) : WaitingFsm {
        override val whoseTurn: Player
            get() = fsm.whoseTurn

        override fun resolve(): ResolveResult? {
            val r = event.inFrontOfWhom
            val g = r.game!!
            g.players.send { p ->
                skillJingMengAToc {
                    playerId = p.getAlternativeLocation(r.location)
                    targetPlayerId = p.getAlternativeLocation(target.location)
                    waitingSecond = Config.WaitSecond
                    if (p === r) {
                        target.cards.forEach { cards.add(it.toPbCard()) }
                        val seq2 = p.seq
                        seq = seq2
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq2)) {
                                p.game!!.tryContinueResolveProtocol(p, skillJingMengBTos {
                                    cardId = target.cards.first().id
                                    seq = seq2
                                })
                            }
                        }, p.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    r.game!!.tryContinueResolveProtocol(r, skillJingMengBTos {
                        cardId = target.cards.bestCard(r.identity).id
                    })
                }, 3, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== event.inFrontOfWhom) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_jing_meng_b_tos) {
                logger.error("错误的协议")
                player.sendErrorMessage("错误的协议")
                return null
            }
            val r = event.inFrontOfWhom
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            val card = target.findCard(message.cardId)
            if (card == null) {
                logger.error("没有这张牌")
                player.sendErrorMessage("没有这张牌")
                return null
            }
            r.incrSeq()
            logger.info("${r}弃掉了${target}的$card")
            g.players.send {
                skillJingMengBToc {
                    playerId = it.getAlternativeLocation(r.location)
                    targetPlayerId = it.getAlternativeLocation(target.location)
                    this.card = card.toPbCard()
                }
            }
            g.playerDiscardCard(target, card)
            g.addEvent(DiscardCardEvent(event.whoseTurn, target))
            return ResolveResult(fsm, true)
        }
    }

    companion object {
        fun ai(fsm0: Fsm): Boolean {
            if (fsm0 !is ExecuteJingMengA) return false
            val p = fsm0.event.inFrontOfWhom
            val target = p.game!!.players.filter {
                it!!.alive && p.isEnemy(it) && it.cards.isNotEmpty()
            }.randomOrNull() ?: return false
            GameExecutor.post(p.game!!, {
                p.game!!.tryContinueResolveProtocol(p, skillJingMengATos {
                    targetPlayerId = p.getAlternativeLocation(target.location)
                })
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}
