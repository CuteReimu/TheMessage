package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.sortCards
import com.fengsheng.card.Card
import com.fengsheng.card.WeiBi
import com.fengsheng.card.count
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Role.*
import com.fengsheng.skill.SkillId.JIAO_JI
import com.google.protobuf.GeneratedMessageV3
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 裴玲技能【交际】：出牌阶段限一次，你可以抽取一名角色的最多两张手牌。然后将等量手牌交给该角色。你每收集一张黑色情报，便可以少交一张牌。
 */
class JiaoJi : MainPhaseSkill() {
    override val skillId = JIAO_JI

    override val isInitialSkill = true

    override fun mainPhaseNeedNotify(r: Player): Boolean =
        super.mainPhaseNeedNotify(r) && r.game!!.players.any { it !== r && it!!.alive && it.cards.isNotEmpty() }

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        val fsm = g.fsm as? MainPhaseIdle
        if (r !== fsm?.whoseTurn) {
            logger.error("现在不是出牌阶段空闲时点")
            (r as? HumanPlayer)?.sendErrorMessage("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            logger.error("[交际]一回合只能发动一次")
            (r as? HumanPlayer)?.sendErrorMessage("[交际]一回合只能发动一次")
            return
        }
        val pb = message as skill_jiao_ji_a_tos
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
        if (target.cards.isEmpty()) {
            logger.error("目标没有手牌")
            (r as? HumanPlayer)?.sendErrorMessage("目标没有手牌")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        val cards: List<Card>
        if (target.cards.size <= 2) {
            cards = target.cards.toList()
            target.cards.clear()
        } else {
            cards = List(2) { target.deleteCard(target.cards.random().id)!! }
        }
        logger.info("${r}对${target}发动了[交际]，抽取了${cards.joinToString()}")
        r.cards.addAll(cards)
        val black = r.messageCards.count(color.Black)
        val needReturnCount = (cards.size - black).coerceAtLeast(0)..cards.size
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_jiao_ji_a_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(r.location)
                builder.targetPlayerId = p.getAlternativeLocation(target.location)
                if (p === r || p === target) {
                    for (card in cards) builder.addCards(card.toPbCard())
                } else {
                    builder.unknownCardCount = cards.size
                }
                builder.waitingSecond = Config.WaitSecond
                if (p === r) {
                    val seq = p.seq
                    builder.seq = seq
                    p.timeout = GameExecutor.post(g, {
                        if (p.checkSeq(seq)) {
                            val builder2 = skill_jiao_ji_b_tos.newBuilder()
                            for (c in r.cards) {
                                if (builder2.cardIdsCount >= needReturnCount.first) break
                                builder2.addCardIds(c.id)
                            }
                            builder2.seq = seq
                            g.tryContinueResolveProtocol(r, builder2.build())
                        }
                    }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                }
                p.send(builder.build())
            }
        }
        if (r is RobotPlayer) {
            GameExecutor.post(g, {
                val builder2 = skill_jiao_ji_b_tos.newBuilder()
                for (c in r.cards.sortCards(r.identity, true)) {
                    if (builder2.cardIdsCount >= needReturnCount.first) break
                    builder2.addCardIds(c.id)
                }
                g.tryContinueResolveProtocol(r, builder2.build())
            }, 3, TimeUnit.SECONDS)
        }
        g.resolve(executeJiaoJi(fsm, target, needReturnCount))
    }

    private data class executeJiaoJi(val fsm: MainPhaseIdle, val target: Player, val needReturnCount: IntRange) :
        WaitingFsm {
        override fun resolve(): ResolveResult? {
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.whoseTurn) {
                logger.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_jiao_ji_b_tos) {
                logger.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            if (message.cardIdsCount !in needReturnCount) {
                logger.error("卡牌数量不正确，需要返还：${needReturnCount}，实际返还：${message.cardIdsCount}")
                (player as? HumanPlayer)?.sendErrorMessage("卡牌数量不正确，需要返还：${needReturnCount}，实际返还：${message.cardIdsCount}")
                return null
            }
            val r = fsm.whoseTurn
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            val cards = List(message.cardIdsCount) {
                val card = r.findCard(message.getCardIds(it))
                if (card == null) {
                    logger.error("没有这张卡")
                    (player as? HumanPlayer)?.sendErrorMessage("没有这张卡")
                    return null
                }
                card
            }
            r.incrSeq()
            logger.info("${r}将${cards.joinToString()}还给$target")
            r.cards.removeAll(cards.toSet())
            target.cards.addAll(cards)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jiao_ji_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    if (p === r || p === target) {
                        for (card in cards) builder.addCards(card.toPbCard())
                    } else {
                        builder.unknownCardCount = cards.size
                    }
                    p.send(builder.build())
                }
            }
            g.addEvent(GiveCardEvent(r, target, r))
            if (cards.isNotEmpty()) {
                g.addEvent(GiveCardEvent(r, r, target))
                if (cards.any { it.type in WeiBi.availableCardType }) r.weiBiFailRate = 0
            }
            return ResolveResult(fsm, true)
        }
    }

    companion object {
        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseTurn
            player.getSkillUseCount(JIAO_JI) == 0 || return false
            val players = player.game!!.players.filter { it !== player && it!!.alive && it.cards.isNotEmpty() }
            val target = players.filter { player.isEnemy(it!!) && it.cards.size >= 2 }
                .ifEmpty { players.filter { player.isEnemy(it!!) } }
                .ifEmpty { players }.randomOrNull() ?: return false
            GameExecutor.post(player.game!!, {
                val builder = skill_jiao_ji_a_tos.newBuilder()
                builder.targetPlayerId = player.getAlternativeLocation(target.location)
                skill.executeProtocol(player.game!!, player, builder.build())
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}