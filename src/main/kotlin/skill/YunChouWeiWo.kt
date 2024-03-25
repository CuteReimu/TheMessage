package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.sortCards
import com.fengsheng.card.Card
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.protos.Role.skill_yun_chou_wei_wo_a_tos
import com.fengsheng.protos.Role.skill_yun_chou_wei_wo_b_tos
import com.fengsheng.protos.skillYunChouWeiWoAToc
import com.fengsheng.protos.skillYunChouWeiWoATos
import com.fengsheng.protos.skillYunChouWeiWoBToc
import com.fengsheng.protos.skillYunChouWeiWoBTos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 老虎技能【运筹帷幄】：出牌阶段或争夺阶段，你可以翻开此角色牌，然后查看牌堆顶的五张牌，从中选择三张加入手牌，其余的卡牌按任意顺序放回牌堆顶。
 */
class YunChouWeiWo : ActiveSkill {
    override val skillId = SkillId.YUN_CHOU_WEI_WO

    override val isInitialSkill = true

    override fun canUse(fightPhase: FightPhaseIdle, r: Player): Boolean = !r.roleFaceUp

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessage) {
        val fsm = g.fsm
        when (fsm) {
            is MainPhaseIdle -> {
                if (r !== fsm.whoseTurn) {
                    logger.error("现在不是发动[运筹帷幄]的时机")
                    r.sendErrorMessage("现在不是发动[运筹帷幄]的时机")
                    return
                }
            }

            is FightPhaseIdle -> {
                if (r !== fsm.whoseFightTurn) {
                    logger.error("现在不是发动[运筹帷幄]的时机")
                    r.sendErrorMessage("现在不是发动[运筹帷幄]的时机")
                    return
                }
            }

            else -> {
                logger.error("现在不是发动[运筹帷幄]的时机")
                r.sendErrorMessage("现在不是发动[运筹帷幄]的时机")
                return
            }
        }
        if (r.roleFaceUp) {
            logger.error("你现在正面朝上，不能发动[运筹帷幄]")
            r.sendErrorMessage("你现在正面朝上，不能发动[运筹帷幄]")
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
            r.sendErrorMessage("牌堆中的牌不够了")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        g.resolve(ExecuteYunChouWeiWo(fsm, r, cards))
    }

    private data class ExecuteYunChouWeiWo(val fsm: Fsm, val r: Player, val cards: List<Card>) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game!!
            logger.info("${r}发动了[运筹帷幄]，查看了牌堆顶的五张牌")
            g.players.send { p ->
                skillYunChouWeiWoAToc {
                    playerId = p.getAlternativeLocation(r.location)
                    waitingSecond = Config.WaitSecond * 2
                    if (p === r) {
                        this@ExecuteYunChouWeiWo.cards.forEach { cards.add(it.toPbCard()) }
                        val seq2 = p.seq
                        seq = seq2
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq2)) {
                                g.tryContinueResolveProtocol(r, skillYunChouWeiWoBTos {
                                    deckCardIds.add(this@ExecuteYunChouWeiWo.cards[1].id)
                                    deckCardIds.add(this@ExecuteYunChouWeiWo.cards[0].id)
                                    seq = seq2
                                })
                                return@post
                            }
                        }, p.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    val sortedCards = cards.sortCards(r.identity)
                    g.tryContinueResolveProtocol(r, skillYunChouWeiWoBTos {
                        deckCardIds.add(sortedCards[3].id)
                        deckCardIds.add(sortedCards[4].id)
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
            if (message !is skill_yun_chou_wei_wo_b_tos) {
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
            if (message.deckCardIdsCount != 2) {
                logger.error("你必须选择两张牌放回牌堆顶")
                player.sendErrorMessage("你必须选择两张牌放回牌堆顶")
                return null
            }
            val deckCards = message.deckCardIdsList.map {
                val card = cards.find { card -> card.id == it }
                if (card == null) {
                    logger.error("没有这张牌")
                    player.sendErrorMessage("没有这张牌")
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
            g.players.send { p ->
                skillYunChouWeiWoBToc {
                    playerId = p.getAlternativeLocation(r.location)
                    if (p === r) handCards.forEach { cards.add(it.toPbCard()) }
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
            !player.roleFaceUp || return false
            if (e is FightPhaseIdle) {
                player.game!!.players.anyoneWillWinOrDie(e) || return false
            }
            GameExecutor.post(player.game!!, {
                skill.executeProtocol(player.game!!, player, skillYunChouWeiWoATos { })
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}
