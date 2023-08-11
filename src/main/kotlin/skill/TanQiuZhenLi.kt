package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.card.count
import com.fengsheng.phase.CheckWin
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.OnAddMessageCard
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.color.Blue
import com.fengsheng.protos.Common.color.Red
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * SP连鸢技能【探求真理】：出牌阶段限一次，你可以从另一名角色的情报区中选择一张情报，将其置入你的情报区，但不能以此令你收集三张或更多同色情报。然后该角色可以将其手牌或情报区中的一张纯黑色牌置入你的情报区。
 */
class TanQiuZhenLi : AbstractSkill(), ActiveSkill {
    override val skillId = SkillId.TAN_QIU_ZHEN_LI

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (r !== (g.fsm as? MainPhaseIdle)?.player) {
            log.error("现在不是出牌阶段空闲时点")
            (r as? HumanPlayer)?.sendErrorMessage("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            log.error("[探求真理]一回合只能发动一次")
            (r as? HumanPlayer)?.sendErrorMessage("[探求真理]一回合只能发动一次")
            return
        }
        val pb = message as skill_tan_qiu_zhen_li_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        if (pb.targetPlayerId < 0 || pb.targetPlayerId >= g.players.size) {
            log.error("目标错误")
            (r as? HumanPlayer)?.sendErrorMessage("目标错误")
            return
        }
        val target = g.players[r.getAbstractLocation(pb.targetPlayerId)]!!
        if (!target.alive) {
            log.error("目标已死亡")
            (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
            return
        }
        val card = target.findMessageCard(pb.cardId)
        if (card == null) {
            log.error("没有这张情报")
            (r as? HumanPlayer)?.sendErrorMessage("没有这张情报")
            return
        }
        if (r.checkThreeSameMessageCard(card)) {
            log.error("你不能以此技能令你收集三张或更多同色情报")
            (r as? HumanPlayer)?.sendErrorMessage("你不能以此技能令你收集三张或更多同色情报")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        log.info("${r}发动了[探求真理]，将${target}面前的${card}移到自己面前")
        target.deleteMessageCard(card.id)
        r.messageCards.add(card)
        val waitingSecond = 15
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_tan_qiu_zhen_li_a_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(r.location)
                builder.targetPlayerId = p.getAlternativeLocation(target.location)
                builder.cardId = card.id
                builder.waitingSecond = waitingSecond
                if (p === target) builder.seq = target.seq
                p.send(builder.build())
            }
        }
        g.resolve(executeTanQiuZhenLi(r, target, waitingSecond))
    }

    private data class executeTanQiuZhenLi(val r: Player, val target: Player, val waitingSecond: Int) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            if (target is HumanPlayer) {
                val seq = target.seq
                target.timeout = GameExecutor.post(target.game!!, {
                    if (target.checkSeq(seq)) {
                        val builder = skill_tan_qiu_zhen_li_b_tos.newBuilder()
                        builder.enable = false
                        builder.seq = seq
                        target.game!!.tryContinueResolveProtocol(target, builder.build())
                    }
                }, target.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
            } else {
                GameExecutor.post(target.game!!, {
                    val builder = skill_tan_qiu_zhen_li_b_tos.newBuilder()
                    val messageCard = target.messageCards.find { it.isPureBlack() }
                    if (messageCard != null) {
                        builder.enable = true
                        builder.fromHand = false
                        builder.cardId = messageCard.id
                    } else {
                        val card = target.cards.find { it.isPureBlack() }
                        if (card != null) {
                            builder.enable = true
                            builder.fromHand = true
                            builder.cardId = card.id
                        }
                    }
                    target.game!!.tryContinueResolveProtocol(target, builder.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== target) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_tan_qiu_zhen_li_b_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val g = r.game!!
            if (target is HumanPlayer && !target.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${target.seq}, actual Seq: ${message.seq}")
                target.sendErrorMessage("操作太晚了")
                return null
            }
            if (!message.enable) {
                target.incrSeq()
                for (p in g.players) {
                    if (p is HumanPlayer) {
                        val builder = skill_tan_qiu_zhen_li_b_toc.newBuilder()
                        builder.enable = false
                        builder.targetPlayerId = p.getAlternativeLocation(target.location)
                        builder.playerId = p.getAlternativeLocation(r.location)
                        p.send(builder.build())
                    }
                }
                return ResolveResult(OnAddMessageCard(r, MainPhaseIdle(r)), true)
            }
            val card =
                if (message.fromHand) {
                    val card1 = target.deleteCard(message.cardId)
                    if (card1 == null) {
                        log.error("没有这张牌")
                        (target as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                        return null
                    }
                    log.info("${target}将手牌中的${card1}置入${r}的情报区")
                    card1
                } else {
                    val card1 = target.deleteMessageCard(message.cardId)
                    if (card1 == null) {
                        log.error("没有这张情报")
                        (target as? HumanPlayer)?.sendErrorMessage("没有这张情报")
                        return null
                    }
                    log.info("${target}将情报区的${card1}置入${r}的情报区")
                    card1
                }
            target.incrSeq()
            r.messageCards.add(card)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_tan_qiu_zhen_li_b_toc.newBuilder()
                    builder.enable = true
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.fromHand = message.fromHand
                    builder.card = card.toPbCard()
                    p.send(builder.build())
                }
            }
            val newFsm = CheckWin(r, MainPhaseIdle(r))
            newFsm.receiveOrder.addPlayerIfHasThreeBlack(r)
            return ResolveResult(OnAddMessageCard(r, newFsm), true)
        }

        companion object {
            private val log = Logger.getLogger(executeTanQiuZhenLi::class.java)
        }
    }

    companion object {
        private val log = Logger.getLogger(TanQiuZhenLi::class.java)
        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            if (e.player.getSkillUseCount(SkillId.TAN_QIU_ZHEN_LI) > 0) return false
            val availableColor = ArrayList<color>()
            if (e.player.messageCards.count(Red) < 2) availableColor.add(Red)
            if (e.player.messageCards.count(Blue) < 2) availableColor.add(Blue)
            val color = availableColor.randomOrNull() ?: return false

            fun isPureColor(c: Card) = c.colors.size == 1 && c.colors.first() == color

            val target = e.player.game!!.players.filter {
                it!!.isEnemy(e.player) && it.alive && it.messageCards.any(::isPureColor)
            }.randomOrNull() ?: return false
            val card = target.messageCards.filter(::isPureColor).randomOrNull() ?: return false
            val cardId = card.id
            GameExecutor.post(e.player.game!!, {
                val builder = skill_tan_qiu_zhen_li_a_tos.newBuilder()
                builder.targetPlayerId = e.player.getAlternativeLocation(target.location)
                builder.cardId = cardId
                skill.executeProtocol(e.player.game!!, e.player, builder.build())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}