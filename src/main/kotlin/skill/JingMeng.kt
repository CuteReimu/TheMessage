package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.protos.Fengsheng.end_receive_phase_tos
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 程小蝶技能【惊梦】：你接收黑色情报后，可以查看一名角色的手牌。
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
        return ResolveResult(executeJingMengA(g.fsm!!, event), true)
    }

    private data class executeJingMengA(val fsm: Fsm, val event: ReceiveCardEvent) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in event.whoseTurn.game!!.players)
                p!!.notifyReceivePhase(event.whoseTurn, event.inFrontOfWhom, event.messageCard, event.inFrontOfWhom)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== event.inFrontOfWhom) {
                logger.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
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
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
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
                (player as? HumanPlayer)?.sendErrorMessage("目标错误：${message.targetPlayerId}")
                return null
            }
            val target = g.players[r.getAbstractLocation(message.targetPlayerId)]!!
            if (!target.alive) {
                logger.error("目标已死亡")
                (player as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                return null
            }
            if (target.cards.isEmpty()) {
                logger.error("目标没有手牌")
                (player as? HumanPlayer)?.sendErrorMessage("目标没有手牌")
                return null
            }
            r.incrSeq()
            logger.info("${r}发动了[惊梦]，查看了${target}的手牌")
            return ResolveResult(executeJingMengB(fsm, event, target), true)
        }

        companion object {
        }
    }

    private data class executeJingMengB(val fsm: Fsm, val event: ReceiveCardEvent, val target: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = event.inFrontOfWhom
            val g = r.game!!
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jing_meng_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.waitingSecond = Config.WaitSecond
                    if (p === r) {
                        for (card in target.cards) builder.addCards(card.toPbCard())
                        val seq2: Int = p.seq
                        builder.seq = seq2
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq2)) {
                                val builder2 = skill_jing_meng_b_tos.newBuilder()
                                builder2.cardId = target.cards.first().id
                                builder2.seq = seq2
                                p.game!!.tryContinueResolveProtocol(p, builder2.build())
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    val builder = skill_jing_meng_b_tos.newBuilder()
                    builder.cardId = target.cards.first().id
                    r.game!!.tryContinueResolveProtocol(r, builder.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== event.inFrontOfWhom) {
                logger.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_jing_meng_b_tos) {
                logger.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
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
                (player as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                return null
            }
            r.incrSeq()
            logger.info("${r}弃掉了${target}的$card")
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jing_meng_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.card = card.toPbCard()
                    p.send(builder.build())
                }
            }
            g.playerDiscardCard(target, card)
            g.addEvent(DiscardCardEvent(event.whoseTurn, target))
            return ResolveResult(fsm, true)
        }

        companion object {
        }
    }

    companion object {
        fun ai(fsm0: Fsm): Boolean {
            if (fsm0 !is executeJingMengA) return false
            val p = fsm0.event.inFrontOfWhom
            val target = p.game!!.players.filter {
                it!!.alive && p.isEnemy(it) && it.cards.isNotEmpty()
            }.randomOrNull() ?: return false
            GameExecutor.post(p.game!!, {
                val builder = skill_jing_meng_a_tos.newBuilder()
                builder.targetPlayerId = p.getAlternativeLocation(target.location)
                p.game!!.tryContinueResolveProtocol(p, builder.build())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}