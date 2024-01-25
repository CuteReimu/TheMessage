package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.count
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 秦无命技能【鱼死网破】：出牌阶段限一次，你可以弃置一张手牌，令一名其他角色弃置（你的黑情报数量+1）的手牌（不足则全弃）。
 */
class YuSiWangPo : MainPhaseSkill() {
    override val skillId = SkillId.YU_SI_WANG_PO

    override val isInitialSkill = true

    override fun mainPhaseNeedNotify(r: Player): Boolean =
        super.mainPhaseNeedNotify(r) && r.cards.size > 1

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (r !== (g.fsm as? MainPhaseIdle)?.whoseTurn) {
            logger.error("现在不是出牌阶段空闲时点")
            (r as? HumanPlayer)?.sendErrorMessage("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            logger.error("[鱼死网破]一回合只能发动一次")
            (r as? HumanPlayer)?.sendErrorMessage("[鱼死网破]一回合只能发动一次")
            return
        }
        val pb = message as skill_yu_si_wang_po_a_tos
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
        if (pb.targetPlayerId == 0) {
            logger.error("不能以自己为目标")
            (r as? HumanPlayer)?.sendErrorMessage("不能以自己为目标")
            return
        }
        val target = g.players[r.getAbstractLocation(pb.targetPlayerId)]!!
        if (!target.alive) {
            logger.error("目标已死亡")
            (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
            return
        }
        val card = r.findCard(pb.cardId)
        if (card == null) {
            logger.error("没有这张卡")
            (r as? HumanPlayer)?.sendErrorMessage("没有这张卡")
            return
        }
        val discardCount = r.messageCards.count(Black) + 1
        val discardAll = discardCount >= target.cards.size
        r.incrSeq()
        r.addSkillUseCount(skillId)
        logger.info("${r}对${target}发动了[鱼死网破]")
        val timeout = Config.WaitSecond
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_yu_si_wang_po_a_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(r.location)
                builder.targetPlayerId = p.getAlternativeLocation(target.location)
                if (!discardAll) {
                    builder.waitingSecond = timeout
                    if (p === target) builder.seq = p.seq
                }
                p.send(builder.build())
            }
        }
        g.playerDiscardCard(r, card)
        g.addEvent(DiscardCardEvent(r, r))
        if (target.cards.isNotEmpty()) g.addEvent(DiscardCardEvent(r, target))
        if (discardAll) {
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_yu_si_wang_po_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    p.send(builder.build())
                }
            }
            g.playerDiscardCard(target, *target.cards.toTypedArray())
            g.continueResolve()
        } else {
            g.resolve(executeYuSiWangPo(g.fsm!!, r, target, discardCount, timeout))
        }
    }

    private data class executeYuSiWangPo(
        val fsm: Fsm,
        val r: Player,
        val target: Player,
        val cardCount: Int,
        val waitingSecond: Int
    ) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game!!
            if (target is HumanPlayer) {
                val seq = target.seq
                target.timeout = GameExecutor.post(g, {
                    if (target.checkSeq(seq)) {
                        val builder2 = skill_yu_si_wang_po_b_tos.newBuilder()
                        builder2.addAllCardIds(target.cards.subList(0, cardCount).map { it.id })
                        builder2.seq = seq
                        g.tryContinueResolveProtocol(target, builder2.build())
                    }
                }, target.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
            } else {
                GameExecutor.post(g, {
                    val builder2 = skill_yu_si_wang_po_b_tos.newBuilder()
                    builder2.addAllCardIds(target.cards.shuffled().subList(0, cardCount).map { it.id })
                    g.tryContinueResolveProtocol(target, builder2.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== target) {
                logger.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_yu_si_wang_po_b_tos) {
                logger.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            if (target is HumanPlayer && !target.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${target.seq}, actual Seq: ${message.seq}")
                target.sendErrorMessage("操作太晚了")
                return null
            }
            if (cardCount != message.cardIdsCount) {
                logger.error("选择的卡牌数量不对")
                (player as? HumanPlayer)?.sendErrorMessage("你需要选择${cardCount}张牌")
                return null
            }
            val cards = message.cardIdsList.map {
                val card = target.findCard(it)
                if (card == null) {
                    logger.error("没有这张卡")
                    (target as? HumanPlayer)?.sendErrorMessage("没有这张卡")
                    return null
                }
                card
            }
            target.incrSeq()
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_yu_si_wang_po_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    p.send(builder.build())
                }
            }
            target.game!!.playerDiscardCard(target, *cards.toTypedArray())
            return ResolveResult(fsm, true)
        }
    }

    companion object {
        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            e.whoseTurn.getSkillUseCount(SkillId.YU_SI_WANG_PO) == 0 || return false
            val card = e.whoseTurn.cards.randomOrNull() ?: return false
            val target =
                e.whoseTurn.game!!.players.filter { it!!.alive && it.isEnemy(e.whoseTurn) && it.cards.size >= 2 }
                    .randomOrNull() ?: return false
            GameExecutor.post(e.whoseTurn.game!!, {
                val builder = skill_yu_si_wang_po_a_tos.newBuilder()
                builder.targetPlayerId = e.whoseTurn.getAlternativeLocation(target.location)
                builder.cardId = card.id
                skill.executeProtocol(e.whoseTurn.game!!, e.whoseTurn, builder.build())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}