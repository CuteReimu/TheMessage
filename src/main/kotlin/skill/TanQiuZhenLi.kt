package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.sortCards
import com.fengsheng.card.Card
import com.fengsheng.card.PlayerAndCard
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Role.skill_tan_qiu_zhen_li_a_tos
import com.fengsheng.protos.Role.skill_tan_qiu_zhen_li_b_tos
import com.fengsheng.protos.skillTanQiuZhenLiAToc
import com.fengsheng.protos.skillTanQiuZhenLiATos
import com.fengsheng.protos.skillTanQiuZhenLiBToc
import com.fengsheng.protos.skillTanQiuZhenLiBTos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * SP连鸢技能【探求真理】：出牌阶段限一次，你可以从另一名角色的情报区中选择一张情报，将其置入你的情报区，但不能以此令你收集三张或更多同色情报。然后该角色可以将其手牌或情报区中的一张纯黑色牌置入你的情报区。
 */
class TanQiuZhenLi : MainPhaseSkill() {
    override val skillId = SkillId.TAN_QIU_ZHEN_LI

    override val isInitialSkill = true

    override fun mainPhaseNeedNotify(r: Player) = super.mainPhaseNeedNotify(r) && r.game!!.players.any {
        it !== r && it!!.alive && it.messageCards.any { c -> Red in c.colors || Blue in c.colors }
    }

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessage) {
        if (r !== (g.fsm as? MainPhaseIdle)?.whoseTurn) {
            logger.error("现在不是出牌阶段空闲时点")
            r.sendErrorMessage("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            logger.error("[探求真理]一回合只能发动一次")
            r.sendErrorMessage("[探求真理]一回合只能发动一次")
            return
        }
        val pb = message as skill_tan_qiu_zhen_li_a_tos
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
        val card = target.findMessageCard(pb.cardId)
        if (card == null) {
            logger.error("没有这张情报")
            r.sendErrorMessage("没有这张情报")
            return
        }
        if (r.checkThreeSameMessageCard(card)) {
            logger.error("你不能以此技能令你收集三张或更多同色情报")
            r.sendErrorMessage("你不能以此技能令你收集三张或更多同色情报")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        logger.info("${r}发动了[探求真理]，将${target}面前的${card}移到自己面前")
        target.deleteMessageCard(card.id)
        r.messageCards.add(card)
        val waitingSecond = g.waitSecond
        g.players.send { p ->
            skillTanQiuZhenLiAToc {
                playerId = p.getAlternativeLocation(r.location)
                targetPlayerId = p.getAlternativeLocation(target.location)
                cardId = card.id
                this.waitingSecond = waitingSecond
                if (p === target) seq = target.seq
            }
        }
        g.addEvent(AddMessageCardEvent(r))
        g.resolve(ExecuteTanQiuZhenLi(g.fsm!!, r, target, waitingSecond))
    }

    private class ExecuteTanQiuZhenLi(
        val fsm: Fsm,
        val r: Player,
        val target: Player,
        val waitingSecond: Int
    ) : WaitingFsm {
        override val whoseTurn: Player
            get() = fsm.whoseTurn

        override fun resolve(): ResolveResult? {
            if (target is HumanPlayer) {
                val seq = target.seq
                target.timeout = GameExecutor.post(target.game!!, {
                    if (target.checkSeq(seq))
                        target.game!!.tryContinueResolveProtocol(target, skillTanQiuZhenLiBTos { this.seq = seq })
                }, target.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
            } else {
                GameExecutor.post(target.game!!, {
                    var value = 0
                    var card: Card? = null
                    var fromHand = false
                    for (c in target.messageCards.toList()) {
                        c.isPureBlack() || continue
                        val v = target.calculateRemoveCardValue(r, target, c) +
                            target.calculateMessageCardValue(r, r, c)
                        if (v > value) {
                            value = v
                            card = c
                            fromHand = false
                        }
                    }
                    for (c in target.cards.sortCards(target.identity, true)) {
                        c.isPureBlack() || continue
                        val v = -10 + target.calculateMessageCardValue(r, r, c)
                        if (v > value) {
                            value = v
                            card = c
                            fromHand = true
                        }
                    }
                    target.game!!.tryContinueResolveProtocol(target, skillTanQiuZhenLiBTos {
                        card?.let {
                            enable = true
                            this.fromHand = fromHand
                            cardId = it.id
                        }
                    })
                }, 3, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== target) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_tan_qiu_zhen_li_b_tos) {
                logger.error("错误的协议")
                player.sendErrorMessage("错误的协议")
                return null
            }
            val g = r.game!!
            if (target is HumanPlayer && !target.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${target.seq}, actual Seq: ${message.seq}")
                target.sendErrorMessage("操作太晚了")
                return null
            }
            if (!message.enable) {
                target.incrSeq()
                g.players.send {
                    skillTanQiuZhenLiBToc {
                        enable = false
                        targetPlayerId = it.getAlternativeLocation(target.location)
                        playerId = it.getAlternativeLocation(r.location)
                    }
                }
                return ResolveResult(fsm, true)
            }
            val card =
                if (message.fromHand) {
                    val card1 = target.deleteCard(message.cardId)
                    if (card1 == null) {
                        logger.error("没有这张牌")
                        target.sendErrorMessage("没有这张牌")
                        return null
                    }
                    logger.info("${target}将手牌中的${card1}置入${r}的情报区")
                    card1
                } else {
                    val card1 = target.deleteMessageCard(message.cardId)
                    if (card1 == null) {
                        logger.error("没有这张情报")
                        target.sendErrorMessage("没有这张情报")
                        return null
                    }
                    logger.info("${target}将情报区的${card1}置入${r}的情报区")
                    card1
                }
            target.incrSeq()
            r.messageCards.add(card)
            g.players.send {
                skillTanQiuZhenLiBToc {
                    enable = true
                    targetPlayerId = it.getAlternativeLocation(target.location)
                    playerId = it.getAlternativeLocation(r.location)
                    fromHand = message.fromHand
                    this.card = card.toPbCard()
                }
            }
            return ResolveResult(fsm, true)
        }
    }

    companion object {
        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            e.whoseTurn.getSkillUseCount(SkillId.TAN_QIU_ZHEN_LI) == 0 || return false
            val player = e.whoseTurn
            var target: PlayerAndCard? = null
            var value = 0
            for (p in e.whoseTurn.game!!.players.shuffled()) {
                p!!.alive && p !== player || continue
                for (c in p.messageCards.toList()) {
                    !player.checkThreeSameMessageCard(c) || continue
                    val v1 = player.calculateRemoveCardValue(player, p, c)
                    val v2 = player.calculateMessageCardValue(player, player, c)
                    val index = p.messageCards.indexOfFirst { card -> c.id == card.id }
                    if (index >= 0) p.messageCards.removeAt(index)
                    player.messageCards.add(c)
                    val blackHandCard = p.cards.sortCards(p.identity, true).find { it.isPureBlack() }
                    var vOther3 = 0
                    if (blackHandCard != null)
                        vOther3 = -10 + p.calculateMessageCardValue(player, player, blackHandCard)
                    val blackMessageCard = p.messageCards.find { it.id != c.id && it.isPureBlack() }
                    var vOther4 = 0
                    if (blackMessageCard != null)
                        vOther4 = p.calculateRemoveCardValue(player, p, blackMessageCard) +
                            p.calculateMessageCardValue(player, player, blackMessageCard)
                    if (vOther3 > 0 || vOther4 > 0) {
                        if (vOther3 > vOther4) { // 从对方角度看，从手牌放分高
                            var v3 = player.calculateMessageCardValue(player, player, blackHandCard!!)
                            if (p.identity != Black && player.identity != Black) {
                                if (player.identity == p.identity) v3 -= 10
                                else v3 += 10
                            }
                            if (v1 + v2 + v3 > value) {
                                value = v1 + v2 + v3
                                target = PlayerAndCard(p, c)
                            }
                        } else { // 从对方角度看，从情报区放分高
                            val v4 = player.calculateRemoveCardValue(player, p, blackMessageCard!!) +
                                player.calculateMessageCardValue(player, player, blackMessageCard)
                            if (v1 + v2 + v4 > value) {
                                value = v1 + v2 + v4
                                target = PlayerAndCard(p, c)
                            }
                        }
                    } else { // 对方不放
                        if (v1 + v2 > value) {
                            value = v1 + v2
                            target = PlayerAndCard(p, c)
                        }
                    }
                    player.messageCards.removeLast()
                    if (index >= 0) p.messageCards.add(index, c)
                }
            }
            if (target == null) return false
            GameExecutor.post(e.whoseTurn.game!!, {
                skill.executeProtocol(e.whoseTurn.game!!, e.whoseTurn, skillTanQiuZhenLiATos {
                    targetPlayerId = e.whoseTurn.getAlternativeLocation(target.player.location)
                    cardId = target.card.id
                })
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}
