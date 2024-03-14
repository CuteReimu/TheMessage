package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.bestCard
import com.fengsheng.card.Card
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 高桥智子技能【惑心】：出牌阶段限一次，展示牌堆顶的一张牌，然后查看一名角色的手牌，从中选择一张弃置，若弃置了含有展示牌颜色的牌，则将该弃置牌加入你的手牌。
 */
class HuoXin : MainPhaseSkill() {
    override val skillId = SkillId.HUO_XIN

    override val isInitialSkill = true

    override fun mainPhaseNeedNotify(r: Player): Boolean =
        super.mainPhaseNeedNotify(r) && r.game!!.players.any { it !== r && it!!.alive && it.cards.isNotEmpty() }

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (r !== (g.fsm as? MainPhaseIdle)?.whoseTurn) {
            logger.error("现在不是出牌阶段空闲时点")
            (r as? HumanPlayer)?.sendErrorMessage("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            logger.error("[惑心]一回合只能发动一次")
            (r as? HumanPlayer)?.sendErrorMessage("[惑心]一回合只能发动一次")
            return
        }
        val pb = message as skill_huo_xin_a_tos
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
        val showCards = g.deck.peek(1)
        if (showCards.isEmpty()) {
            logger.error("牌堆没牌了")
            (r as? HumanPlayer)?.sendErrorMessage("牌堆没牌了")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        logger.info("${r}发动了[惑心]，展示了牌堆顶的${showCards[0]}，查看了${target}的手牌")
        val waitingSecond = Config.WaitSecond
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_huo_xin_a_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(r.location)
                builder.targetPlayerId = p.getAlternativeLocation(target.location)
                builder.showCard = showCards[0].toPbCard()
                if (target.cards.isNotEmpty()) {
                    builder.waitingSecond = waitingSecond
                    if (p === r) {
                        target.cards.forEach { builder.addCards(it.toPbCard()) }
                        builder.seq = p.seq
                    }
                }
                p.send(builder.build())
            }
        }
        if (target.cards.isEmpty()) {
            g.continueResolve()
            return
        }
        r.weiBiFailRate = 0
        g.resolve(executeHuoXin(g.fsm!!, r, target, showCards[0], waitingSecond))
    }

    private data class executeHuoXin(
        val fsm: Fsm,
        val r: Player,
        val target: Player,
        val showCard: Card,
        val waitingSecond: Int
    ) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            if (r is HumanPlayer) {
                val card = target.cards.run { find { it.hasSameColor(showCard) } ?: first() }
                val seq = r.seq
                r.timeout = GameExecutor.post(r.game!!, {
                    if (r.checkSeq(seq)) {
                        val builder = skill_huo_xin_b_tos.newBuilder()
                        builder.discardCardId = card.id
                        builder.seq = seq
                        r.game!!.tryContinueResolveProtocol(r, builder.build())
                    }
                }, r.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
            } else {
                val card = target.cards.run {
                    filter { it.hasSameColor(showCard) }.ifEmpty { this }.bestCard(r.identity)
                }
                GameExecutor.post(r.game!!, {
                    val builder = skill_huo_xin_b_tos.newBuilder()
                    builder.discardCardId = card.id
                    r.game!!.tryContinueResolveProtocol(r, builder.build())
                }, 3, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                logger.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_huo_xin_b_tos) {
                logger.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            val card = target.deleteCard(message.discardCardId)
            if (card == null) {
                logger.error("没有这张牌")
                (player as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                return null
            }
            r.incrSeq()
            val joinIntoHand = card.hasSameColor(showCard)
            if (joinIntoHand) {
                logger.info("${r}弃掉了${target}的${card}，并加入自己的手牌")
                r.cards.add(card)
            } else {
                logger.info("${r}弃掉了${target}的${card}")
                r.game!!.deck.discard(card)
            }
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_huo_xin_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.discardCard = card.toPbCard()
                    builder.joinIntoHand = joinIntoHand
                    p.send(builder.build())
                }
            }
            r.game!!.addEvent(DiscardCardEvent(r, target))
            return ResolveResult(fsm, true)
        }
    }

    companion object {
        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            if (e.whoseTurn.getSkillUseCount(SkillId.HUO_XIN) > 0) return false
            val isEarly = e.whoseTurn.game!!.isEarly
            val target = e.whoseTurn.game!!.players.filter {
                it !== e.whoseTurn && it!!.alive && (isEarly || it.isEnemy(e.whoseTurn)) && it.cards.isNotEmpty()
            }.randomOrNull() ?: return false
            GameExecutor.post(e.whoseTurn.game!!, {
                val builder = skill_huo_xin_a_tos.newBuilder()
                builder.targetPlayerId = e.whoseTurn.getAlternativeLocation(target.location)
                skill.executeProtocol(e.whoseTurn.game!!, e.whoseTurn, builder.build())
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}