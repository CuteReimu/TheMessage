package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.sortCards
import com.fengsheng.card.Card
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.NextTurn
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Role.skill_miao_shou_a_tos
import com.fengsheng.protos.Role.skill_miao_shou_b_tos
import com.fengsheng.protos.skillMiaoShouAToc
import com.fengsheng.protos.skillMiaoShouATos
import com.fengsheng.protos.skillMiaoShouBToc
import com.fengsheng.protos.skillMiaoShouBTos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 阿芙罗拉技能【妙手】：争夺阶段，你可以翻开此角色牌，然后弃置待接收情报，并查看一名角色的手牌和情报区，从中选择一张牌作为待收情报，面朝上移至一名角色的面前。
 */
class MiaoShou : ActiveSkill {
    override val skillId = SkillId.MIAO_SHOU

    override val isInitialSkill = true

    override fun canUse(fightPhase: FightPhaseIdle, r: Player): Boolean = !r.roleFaceUp

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessage) {
        val fsm = g.fsm as? FightPhaseIdle
        if (r !== fsm?.whoseFightTurn) {
            logger.error("现在不是发动[妙手]的时机")
            r.sendErrorMessage("现在不是发动[妙手]的时机")
            return
        }
        if (r.roleFaceUp) {
            logger.error("你现在正面朝上，不能发动[妙手]")
            r.sendErrorMessage("你现在正面朝上，不能发动[妙手]")
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
            r.sendErrorMessage("目标错误")
            return
        }
        val target = g.players[r.getAbstractLocation(pb.targetPlayerId)]!!
        if (!target.alive) {
            logger.error("目标已死亡")
            r.sendErrorMessage("目标已死亡")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        g.deck.discard(fsm.messageCard)
        if (target.cards.isEmpty() && target.messageCards.isEmpty()) {
            logger.info("${target}没有手牌，也没有情报，回合结束")
            g.resolve(NextTurn(fsm.whoseTurn))
            return
        }
        r.weiBiFailRate = 0
        g.resolve(ExecuteMiaoShou(fsm, r, target))
    }

    private data class ExecuteMiaoShou(val fsm: FightPhaseIdle, val r: Player, val target: Player) : WaitingFsm {
        override val whoseTurn: Player
            get() = fsm.whoseTurn

        override fun resolve(): ResolveResult? {
            val g = r.game!!
            logger.info("${r}对${target}发动了[妙手]")
            g.players.send { p ->
                skillMiaoShouAToc {
                    playerId = p.getAlternativeLocation(r.location)
                    targetPlayerId = p.getAlternativeLocation(target.location)
                    waitingSecond = Config.WaitSecond
                    messageCard = fsm.messageCard.toPbCard()
                    if (p === r) {
                        target.cards.forEach { cards.add(it.toPbCard()) }
                        val seq2 = p.seq
                        seq = seq2
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq2)) {
                                g.tryContinueResolveProtocol(r, skillMiaoShouBTos {
                                    cardId = target.cards.firstOrNull()?.id ?: 0
                                    if (cardId == 0)
                                        messageCardId = target.messageCards.firstOrNull()?.id ?: 0
                                    targetPlayerId = 0
                                    seq = seq2
                                })
                            }
                        }, p.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    g.tryContinueResolveProtocol(r, skillMiaoShouBTos {
                        var value = Int.MIN_VALUE
                        val players = g.players.filterNotNull().filter { it.alive }.shuffled()
                        val handCardScore = if (target.isEnemy(r)) 10 else -10
                        for (card in target.cards.sortCards(r.identity, handCardScore < 0)) {
                            for (moveTarget in players) {
                                val v = r.calculateMessageCardValue(fsm.whoseTurn, moveTarget, card) + handCardScore
                                if (v > value) {
                                    value = v
                                    cardId = card.id
                                    messageCardId = 0
                                    targetPlayerId = r.getAlternativeLocation(moveTarget.location)
                                }
                            }
                        }
                        for (card in target.messageCards.toList()) {
                            val removeValue = r.calculateRemoveCardValue(fsm.whoseTurn, target, card)
                            for (moveTarget in players) {
                                val v = r.calculateMessageCardValue(fsm.whoseTurn, moveTarget, card) + removeValue
                                if (v > value) {
                                    value = v
                                    cardId = 0
                                    messageCardId = card.id
                                    targetPlayerId = r.getAlternativeLocation(moveTarget.location)
                                }
                            }
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
            if (message !is skill_miao_shou_b_tos) {
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
            if (message.cardId != 0 && message.messageCardId != 0) {
                logger.error("只能选择手牌或情报其中之一")
                player.sendErrorMessage("只能选择手牌或情报其中之一")
                return null
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                logger.error("目标错误")
                player.sendErrorMessage("目标错误")
                return null
            }
            val target2 = g.players[r.getAbstractLocation(message.targetPlayerId)]!!
            if (!target2.alive) {
                logger.error("目标已死亡")
                player.sendErrorMessage("目标已死亡")
                return null
            }
            val card: Card?
            if (message.cardId == 0 && message.messageCardId == 0) {
                logger.error("必须选择一张手牌或情报")
                player.sendErrorMessage("必须选择一张手牌或情报")
                return null
            } else if (message.messageCardId == 0) {
                card = target.deleteCard(message.cardId)
                if (card == null) {
                    logger.error("没有这张牌")
                    player.sendErrorMessage("没有这张牌")
                    return null
                }
            } else {
                card = target.deleteMessageCard(message.messageCardId)
                if (card == null) {
                    logger.error("没有这张牌")
                    player.sendErrorMessage("没有这张牌")
                    return null
                }
            }
            r.incrSeq()
            logger.info("${r}将${card}作为情报，面朝上移至${target2}的面前")
            g.players.send { p ->
                skillMiaoShouBToc {
                    playerId = p.getAlternativeLocation(r.location)
                    fromPlayerId = p.getAlternativeLocation(target.location)
                    targetPlayerId = p.getAlternativeLocation(target2.location)
                    if (message.cardId != 0) this.card = card.toPbCard()
                    else messageCardId = card.id
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
            val g = player.game!!
            !player.roleFaceUp || return false
            if (g.players.any {
                    it!!.isPartnerOrSelf(player) && it.willWin(e.whoseTurn, e.inFrontOfWhom, e.messageCard)
                }) return false
            g.players.any {
                it!!.isEnemy(player) && it.willWin(e.whoseTurn, e.inFrontOfWhom, e.messageCard)
            } || e.inFrontOfWhom.isPartnerOrSelf(player) && e.inFrontOfWhom.willDie(e.messageCard) || return false
            var target: Player? = null
            var count = 0
            fun updateMax(p: Player) {
                val c = p.cards.size + p.messageCards.size
                if (c > count) {
                    target = p
                    count = c
                }
            }
            val players = g.players.filterNotNull().filter { it.alive }.shuffled()
            if (player.identity != Black) {
                for (p in players) {
                    p.identity != player.identity && p.identity != Black || continue
                    updateMax(p)
                }
            }
            if (target == null) {
                for (p in players) {
                    p.isEnemy(player) || continue
                    updateMax(p)
                }
            }
            if (target == null) {
                for (p in players) {
                    p.isPartnerOrSelf(player) || continue
                    updateMax(p)
                }
            }
            target ?: return false
            GameExecutor.post(player.game!!, {
                skill.executeProtocol(player.game!!, player, skillMiaoShouATos {
                    targetPlayerId = player.getAlternativeLocation(target!!.location)
                })
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}
