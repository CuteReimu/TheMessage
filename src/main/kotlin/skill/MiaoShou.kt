package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.card.count
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 阿芙罗拉技能【妙手】：争夺阶段，你可以翻开此角色牌，然后弃置待接收情报，并查看一名角色的手牌和情报区，从中选择一张牌作为待收情报，面朝上移至一名角色的面前。
 */
class MiaoShou : ActiveSkill {
    override val skillId = SkillId.MIAO_SHOU

    override val isInitialSkill = true

    override fun canUse(fightPhase: FightPhaseIdle, r: Player): Boolean = !r.roleFaceUp

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        val fsm = g.fsm as? FightPhaseIdle
        if (r !== fsm?.whoseFightTurn) {
            logger.error("现在不是发动[妙手]的时机")
            (r as? HumanPlayer)?.sendErrorMessage("现在不是发动[妙手]的时机")
            return
        }
        if (r.roleFaceUp) {
            logger.error("你现在正面朝上，不能发动[妙手]")
            (r as? HumanPlayer)?.sendErrorMessage("你现在正面朝上，不能发动[妙手]")
            return
        }
        val pb = message as skill_miao_shou_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        if (pb.targetPlayerId < 0 || pb.targetPlayerId >= g.players.size) {
            logger.error("目标错误")
            (r as? HumanPlayer)?.sendErrorMessage("目标错误")
            return
        }
        val target = g.players[r.getAbstractLocation(pb.targetPlayerId)]!!
        if (!target.alive) {
            logger.error("目标已死亡")
            (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
            return
        }
        if (target.cards.isEmpty() && target.messageCards.isEmpty()) {
            logger.error("目标没有手牌，也没有情报牌")
            (r as? HumanPlayer)?.sendErrorMessage("目标没有手牌，也没有情报牌")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        g.deck.discard(fsm.messageCard)
        g.resolve(executeMiaoShou(fsm, r, target))
    }

    private data class executeMiaoShou(val fsm: FightPhaseIdle, val r: Player, val target: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game!!
            logger.info("${r}对${target}发动了[妙手]")
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_miao_shou_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.waitingSecond = Config.WaitSecond
                    builder.messageCard = fsm.messageCard.toPbCard()
                    if (p === r) {
                        target.cards.forEach { builder.addCards(it.toPbCard()) }
                        val seq2 = p.seq
                        builder.seq = seq2
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq2)) {
                                val builder2 = skill_miao_shou_b_tos.newBuilder()
                                builder2.cardId = target.cards.firstOrNull()?.id ?: 0
                                if (builder2.cardId == 0)
                                    builder2.messageCardId = target.messageCards.firstOrNull()?.id ?: 0
                                builder2.targetPlayerId = 0
                                builder2.seq = seq2
                                g.tryContinueResolveProtocol(r, builder2.build())
                                return@post
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    val builder = skill_miao_shou_b_tos.newBuilder()
                    builder.messageCardId = target.messageCards.firstOrNull()?.id ?: 0
                    if (builder.messageCardId == 0)
                        builder.cardId = target.cards.firstOrNull()?.id ?: 0
                    builder.targetPlayerId = 0
                    g.tryContinueResolveProtocol(r, builder.build())
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
            if (message !is skill_miao_shou_b_tos) {
                logger.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            if (message.cardId != 0 && message.messageCardId != 0) {
                logger.error("只能选择手牌或情报其中之一")
                (player as? HumanPlayer)?.sendErrorMessage("只能选择手牌或情报其中之一")
                return null
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                logger.error("目标错误")
                (player as? HumanPlayer)?.sendErrorMessage("目标错误")
                return null
            }
            val target2 = g.players[r.getAbstractLocation(message.targetPlayerId)]!!
            if (!target2.alive) {
                logger.error("目标已死亡")
                (player as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                return null
            }
            val card: Card?
            if (message.cardId == 0 && message.messageCardId == 0) {
                logger.error("必须选择一张手牌或情报")
                (player as? HumanPlayer)?.sendErrorMessage("必须选择一张手牌或情报")
                return null
            } else if (message.messageCardId == 0) {
                card = target.deleteCard(message.cardId)
                if (card == null) {
                    logger.error("没有这张牌")
                    (player as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                    return null
                }
            } else {
                card = target.deleteMessageCard(message.messageCardId)
                if (card == null) {
                    logger.error("没有这张牌")
                    (player as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                    return null
                }
            }
            r.incrSeq()
            logger.info("${r}将${card}作为情报，面朝上移至${target2}的面前")
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_miao_shou_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.fromPlayerId = p.getAlternativeLocation(target.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target2.location)
                    if (message.cardId != 0) builder.card = card.toPbCard()
                    else builder.messageCardId = card.id
                    p.send(builder.build())
                }
            }
            return ResolveResult(
                fsm.copy(
                    messageCard = card,
                    inFrontOfWhom = target2,
                    whoseFightTurn = target2,
                    isMessageCardFaceUp = true
                ), true
            )
        }
    }

    companion object {
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseFightTurn
            if (player.roleFaceUp) return false
            val p = player.game!!.players.find {
                it!!.alive && player.isEnemy(it)
                        && it.identity != color.Black && it.messageCards.count(it.identity) >= 2
            } ?: return false
            GameExecutor.post(player.game!!, {
                val builder = skill_miao_shou_a_tos.newBuilder()
                builder.targetPlayerId = player.getAlternativeLocation(p.location)
                skill.executeProtocol(player.game!!, player, builder.build())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}