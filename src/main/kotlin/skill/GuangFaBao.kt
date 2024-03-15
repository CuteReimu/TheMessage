package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.sortCards
import com.fengsheng.card.Card
import com.fengsheng.card.WeiBi
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.*
import com.fengsheng.protos.Role.skill_guang_fa_bao_a_tos
import com.fengsheng.protos.Role.skill_guang_fa_bao_b_tos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 小九技能【广发报】：争夺阶段，你可以翻开此角色牌，然后摸三张牌，并且你可以将你的任意张手牌置入任意名角色的情报区。你不能通过此技能让任何角色收集三张或更多的同色情报。
 */
class GuangFaBao : ActiveSkill {
    override val skillId = SkillId.GUANG_FA_BAO

    override val isInitialSkill = true

    override fun canUse(fightPhase: FightPhaseIdle, r: Player): Boolean = !r.roleFaceUp

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessage) {
        val fsm = g.fsm as? FightPhaseIdle
        if (fsm == null || r !== fsm.whoseFightTurn) {
            logger.error("现在不是发动[广发报]的时机")
            r.sendErrorMessage("现在不是发动[广发报]的时机")
            return
        }
        if (r.roleFaceUp) {
            logger.error("你现在正面朝上，不能发动[广发报]")
            r.sendErrorMessage("你现在正面朝上，不能发动[广发报]")
            return
        }
        val pb = message as skill_guang_fa_bao_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        logger.info("${r}发动了[广发报]")
        g.players.send { skillGuangFaBaoAToc { playerId = it.getAlternativeLocation(r.location) } }
        r.draw(3)
        g.resolve(executeGuangFaBao(fsm, r, false))
    }

    private data class executeGuangFaBao(val fsm: FightPhaseIdle, val r: Player, val putCard: Boolean) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game!!
            for (p in g.players) {
                if (p is HumanPlayer) {
                    p.send(skillWaitForGuangFaBaoBToc {
                        playerId = p.getAlternativeLocation(r.location)
                        waitingSecond = Config.WaitSecond * 4 / 3
                        if (p === r) {
                            val seq2 = p.seq
                            seq = seq2
                            p.timeout = GameExecutor.post(g, {
                                if (p.checkSeq(seq2))
                                    g.tryContinueResolveProtocol(r, skillGuangFaBaoBTos { seq = seq2 })
                            }, p.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                        }
                    })
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    val players = g.sortedFrom(g.players.filter { it!!.alive }, r.location)
                    var card: Card? = null
                    var target: Player? = null
                    var value = 0
                    val (importantCards, otherCards) = r.cards.partition { it.type in WeiBi.availableCardType }
                    for (c in otherCards.sortCards(r.identity, true)) {
                        for (p in players) {
                            !p.checkThreeSameMessageCard(c) || continue
                            val v = r.calculateMessageCardValue(fsm.whoseTurn, p, c)
                            if (v > value) {
                                value = v
                                card = c
                                target = p
                            }
                        }
                    }
                    if (card == null) {
                        for (c in importantCards.sortCards(r.identity, true)) {
                            for (p in players) {
                                val v = r.calculateMessageCardValue(fsm.whoseTurn, p, c)
                                if (v > value) {
                                    value = v
                                    card = c
                                    target = p
                                }
                            }
                        }
                        if (card != null && target != null && target.checkThreeSameMessageCard(card)) {
                            card = null
                            target = null
                        }
                    }
                    if (card != null && target != null) {
                        g.tryContinueResolveProtocol(r, skillGuangFaBaoBTos {
                            enable = true
                            targetPlayerId = r.getAlternativeLocation(target.location)
                            cardIds.add(card.id)
                        })
                        return@post
                    }
                    g.tryContinueResolveProtocol(r, skill_guang_fa_bao_b_tos.getDefaultInstance())
                }, 1, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== r) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_guang_fa_bao_b_tos) {
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
            if (!message.enable) {
                r.incrSeq()
                for (p in g.players) {
                    if (p is HumanPlayer) {
                        p.send(skillGuangFaBaoBToc {
                            playerId = p.getAlternativeLocation(r.location)
                            enable = false
                        })
                    }
                }
                if (putCard) g.addEvent(AddMessageCardEvent(fsm.whoseTurn))
                val newFsm = fsm.copy(whoseFightTurn = fsm.inFrontOfWhom)
                return ResolveResult(newFsm, true)
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                logger.error("目标错误")
                player.sendErrorMessage("目标错误")
                return null
            }
            val target = g.players[r.getAbstractLocation(message.targetPlayerId)]!!
            if (!target.alive) {
                logger.error("目标已死亡")
                player.sendErrorMessage("目标已死亡")
                return null
            }
            if (message.cardIdsCount == 0) {
                logger.error("enable为true时至少要发一张牌")
                player.sendErrorMessage("至少要发一张牌")
                return null
            }
            val cards = List(message.cardIdsCount) {
                val card = r.findCard(message.getCardIds(it))
                if (card == null) {
                    logger.error("没有这张卡")
                    player.sendErrorMessage("没有这张卡")
                    return null
                }
                card
            }
            if (target.checkThreeSameMessageCard(cards)) {
                logger.error("你不能通过此技能让任何角色收集三张或更多的同色情报")
                player.sendErrorMessage("你不能通过此技能让任何角色收集三张或更多的同色情报")
                return null
            }
            r.incrSeq()
            logger.info("${r}将${cards.joinToString()}置于${target}的情报区")
            r.cards.removeAll(cards.toSet())
            target.messageCards.addAll(cards)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    p.send(skillGuangFaBaoBToc {
                        enable = true
                        cards.forEach { this.cards.add(it.toPbCard()) }
                        playerId = p.getAlternativeLocation(r.location)
                        targetPlayerId = p.getAlternativeLocation(target.location)
                    })
                }
            }
            if (r.cards.isNotEmpty())
                return ResolveResult(copy(putCard = true), true)
            if (putCard) g.addEvent(AddMessageCardEvent(fsm.whoseTurn))
            val newFsm = fsm.copy(whoseFightTurn = fsm.inFrontOfWhom)
            return ResolveResult(newFsm, true)
        }
    }

    companion object {
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseFightTurn
            !player.roleFaceUp || return false
            player === e.whoseTurn || player.game!!.players.anyoneWillWinOrDie(e) || return false
            GameExecutor.post(player.game!!, {
                skill.executeProtocol(player.game!!, player, skillGuangFaBaoATos { })
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}