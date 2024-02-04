package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.sortCards
import com.fengsheng.card.Card
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 老虎技能【运筹帷幄】：出牌阶段或争夺阶段，你可以翻开此角色牌，然后查看牌堆顶的五张牌，从中选择三张加入手牌，其余的卡牌按任意顺序放回牌堆顶。
 */
class YunChouWeiWo : ActiveSkill {
    override val skillId = SkillId.YUN_CHOU_WEI_WO

    override val isInitialSkill = true

    override fun canUse(fightPhase: FightPhaseIdle, r: Player): Boolean = !r.roleFaceUp

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        val fsm = g.fsm
        when (fsm) {
            is MainPhaseIdle -> {
                if (r !== fsm.whoseTurn) {
                    logger.error("现在不是发动[运筹帷幄]的时机")
                    (r as? HumanPlayer)?.sendErrorMessage("现在不是发动[运筹帷幄]的时机")
                    return
                }
            }

            is FightPhaseIdle -> {
                if (r !== fsm.whoseFightTurn) {
                    logger.error("现在不是发动[运筹帷幄]的时机")
                    (r as? HumanPlayer)?.sendErrorMessage("现在不是发动[运筹帷幄]的时机")
                    return
                }
            }

            else -> {
                logger.error("现在不是发动[运筹帷幄]的时机")
                (r as? HumanPlayer)?.sendErrorMessage("现在不是发动[运筹帷幄]的时机")
                return
            }
        }
        if (r.roleFaceUp) {
            logger.error("你现在正面朝上，不能发动[运筹帷幄]")
            (r as? HumanPlayer)?.sendErrorMessage("你现在正面朝上，不能发动[运筹帷幄]")
            return
        }
        val pb = message as skill_yun_chou_wei_wo_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        val cards = g.deck.peek(5)
        if (cards.size < 5) {
            logger.error("牌堆中的牌不够了")
            (r as? HumanPlayer)?.sendErrorMessage("牌堆中的牌不够了")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        g.resolve(executeYunChouWeiWo(fsm, r, cards))
    }

    private data class executeYunChouWeiWo(val fsm: Fsm, val r: Player, val cards: List<Card>) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game!!
            logger.info("${r}发动了[运筹帷幄]，查看了牌堆顶的五张牌")
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_yun_chou_wei_wo_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.waitingSecond = Config.WaitSecond * 2
                    if (p === r) {
                        val sortedCards = cards.sortCards(r.identity)
                        cards.forEach { builder.addCards(it.toPbCard()) }
                        val seq2 = p.seq
                        builder.seq = seq2
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq2)) {
                                val builder2 = skill_yun_chou_wei_wo_b_tos.newBuilder()
                                builder2.addDeckCardIds(sortedCards[4].id)
                                builder2.addDeckCardIds(sortedCards[3].id)
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
                    val builder = skill_yun_chou_wei_wo_b_tos.newBuilder()
                    builder.addDeckCardIds(cards[3].id)
                    builder.addDeckCardIds(cards[4].id)
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
            if (message !is skill_yun_chou_wei_wo_b_tos) {
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
            if (message.deckCardIdsCount != 2) {
                logger.error("你必须选择两张牌放回牌堆顶")
                (player as? HumanPlayer)?.sendErrorMessage("你必须选择两张牌放回牌堆顶")
                return null
            }
            val deckCards = message.deckCardIdsList.map {
                val card = cards.find { card -> card.id == it }
                if (card == null) {
                    logger.error("没有这张牌")
                    (player as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                    return null
                }
                card
            }
            val handCards = cards.filter { it.id != deckCards[0].id && it.id != deckCards[1].id }
            r.incrSeq()
            logger.info("${r}将${handCards.joinToString()}加入手牌，将${deckCards.joinToString()}放回牌堆顶")
            g.deck.draw(5)
            g.deck.addFirst(listOf(deckCards[1], deckCards[0]))
            r.cards.addAll(handCards)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_yun_chou_wei_wo_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    if (p === r) handCards.forEach { builder.addCards(it.toPbCard()) }
                    p.send(builder.build())
                }
            }
            if (fsm is FightPhaseIdle)
                return ResolveResult(fsm.copy(whoseFightTurn = fsm.inFrontOfWhom), true)
            return ResolveResult(fsm, true)
        }
    }

    companion object {
        fun ai(e: Fsm, skill: ActiveSkill): Boolean {
            val player = if (e is FightPhaseIdle) e.whoseFightTurn else (e as MainPhaseIdle).whoseTurn
            if (player.roleFaceUp) return false
            GameExecutor.post(player.game!!, {
                skill.executeProtocol(player.game!!, player, skill_yun_chou_wei_wo_a_tos.getDefaultInstance())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}