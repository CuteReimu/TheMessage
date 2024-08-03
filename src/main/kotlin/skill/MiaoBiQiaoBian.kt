package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.sortCards
import com.fengsheng.card.Card
import com.fengsheng.card.PlayerAndCard
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Role.skill_miao_bi_qiao_bian_a_tos
import com.fengsheng.protos.Role.skill_miao_bi_qiao_bian_b_tos
import com.fengsheng.protos.skillMiaoBiQiaoBianAToc
import com.fengsheng.protos.skillMiaoBiQiaoBianATos
import com.fengsheng.protos.skillMiaoBiQiaoBianBToc
import com.fengsheng.protos.skillMiaoBiQiaoBianBTos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 连鸢技能【妙笔巧辩】：争夺阶段，你可以翻开此角色牌，然后从所有角色的情报区选择合计至多两张不含有相同颜色的情报，将其加入你的手牌。
 */
class MiaoBiQiaoBian : ActiveSkill {
    override val skillId = SkillId.MIAO_BI_QIAO_BIAN

    override val isInitialSkill = true

    override fun canUse(fightPhase: FightPhaseIdle, r: Player): Boolean = !r.roleFaceUp

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessage) {
        val fsm = g.fsm as? FightPhaseIdle
        if (r !== fsm?.whoseFightTurn) {
            logger.error("现在不是发动[妙笔巧辩]的时机")
            r.sendErrorMessage("现在不是发动[妙笔巧辩]的时机")
            return
        }
        if (r.roleFaceUp) {
            logger.error("你现在正面朝上，不能发动[妙笔巧辩]")
            r.sendErrorMessage("你现在正面朝上，不能发动[妙笔巧辩]")
            return
        }
        val pb = message as skill_miao_bi_qiao_bian_a_tos
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
            logger.error("没有这张牌")
            r.sendErrorMessage("没有这张牌")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        g.resolve(ExecuteMiaoBiQiaoBian(fsm, r, target, card))
    }

    private data class ExecuteMiaoBiQiaoBian(
        val fsm: FightPhaseIdle,
        val r: Player,
        val target1: Player,
        val card1: Card
    ) : WaitingFsm {
        override val whoseTurn: Player
            get() = fsm.whoseTurn

        override fun resolve(): ResolveResult? {
            val g = r.game!!
            target1.deleteMessageCard(card1.id)
            r.cards.add(card1)
            logger.info("${r}发动了[妙笔巧辩]，拿走了${target1}面前的$card1")
            val canTakeAnother = g.players.any {
                it!!.alive && it.messageCards.any { c -> !c.hasSameColor(card1) }
            }
            g.players.send { p ->
                skillMiaoBiQiaoBianAToc {
                    playerId = p.getAlternativeLocation(r.location)
                    targetPlayerId = p.getAlternativeLocation(target1.location)
                    cardId = card1.id
                    if (canTakeAnother) {
                        waitingSecond = Config.WaitSecond
                        if (p === r) {
                            val seq2 = p.seq
                            seq = seq2
                            p.timeout = GameExecutor.post(g, {
                                if (p.checkSeq(seq2))
                                    g.tryContinueResolveProtocol(r, skillMiaoBiQiaoBianBTos { seq = seq2 })
                            }, p.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                        }
                    }
                }
            }
            if (canTakeAnother && r is RobotPlayer) {
                GameExecutor.post(g, {
                    val target = fsm.inFrontOfWhom
                    target.messageCards.add(fsm.messageCard) // 假装那个人先获得了这张情报
                    var playerAndCard: PlayerAndCard? = null
                    var value = -11
                    for (p in g.players) {
                        if (p!!.alive) {
                            for (c in p.messageCards.sortCards(r.identity)) {
                                c !== fsm.messageCard || continue
                                !card1.hasSameColor(c) || continue
                                val v = r.calculateRemoveCardValue(fsm.whoseTurn, p, c)
                                if (v > value) {
                                    value = v
                                    playerAndCard = PlayerAndCard(p, c)
                                }
                            }
                        }
                    }
                    target.deleteMessageCard(fsm.messageCard.id)
                    if (playerAndCard == null) {
                        g.tryContinueResolveProtocol(r, skillMiaoBiQiaoBianBTos {})
                    } else {
                        g.tryContinueResolveProtocol(r, skillMiaoBiQiaoBianBTos {
                            enable = true
                            targetPlayerId = r.getAlternativeLocation(playerAndCard.player.location)
                            cardId = playerAndCard.card.id
                        })
                    }
                }, 3, TimeUnit.SECONDS)
            }
            if (!canTakeAnother) {
                g.players.send { skillMiaoBiQiaoBianBToc { playerId = it.getAlternativeLocation(r.location) } }
                return ResolveResult(fsm.copy(whoseFightTurn = fsm.inFrontOfWhom), true)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== r) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_miao_bi_qiao_bian_b_tos) {
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
                g.players.send { skillMiaoBiQiaoBianBToc { playerId = it.getAlternativeLocation(r.location) } }
                return ResolveResult(fsm.copy(whoseFightTurn = fsm.inFrontOfWhom), true)
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
            val card2 = target2.findMessageCard(message.cardId)
            if (card2 == null) {
                logger.error("没有这张牌")
                player.sendErrorMessage("没有这张牌")
                return null
            }
            if (card2.hasSameColor(card1)) {
                logger.error("两张牌含有相同颜色")
                player.sendErrorMessage("两张牌含有相同颜色")
                return null
            }
            r.incrSeq()
            logger.info("${r}拿走了${target2}面前的$card2")
            target2.deleteMessageCard(card2.id)
            r.cards.add(card2)
            g.players.send {
                skillMiaoBiQiaoBianBToc {
                    cardId = card2.id
                    playerId = it.getAlternativeLocation(r.location)
                    targetPlayerId = it.getAlternativeLocation(target2.location)
                    enable = true
                }
            }
            return ResolveResult(fsm.copy(whoseFightTurn = fsm.inFrontOfWhom), true)
        }
    }

    companion object {
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseFightTurn
            val target = e.inFrontOfWhom
            val g = player.game!!
            !player.roleFaceUp || return false
            if (g.players.any {
                    it!!.isPartnerOrSelf(player) && it.willWin(e.whoseTurn, e.inFrontOfWhom, e.messageCard)
                }) return false
            g.players.any {
                it!!.isEnemy(player) && it.willWin(e.whoseTurn, e.inFrontOfWhom, e.messageCard)
            } || target.isPartnerOrSelf(player) && target.willDie(e.messageCard) || return false
            target.messageCards.add(e.messageCard) // 假装那个人先获得了这张情报
            var playerAndCard: PlayerAndCard? = null
            var value = -11
            for (p in g.players) {
                if (p!!.alive) {
                    for (c in p.messageCards.sortCards(player.identity)) {
                        c !== e.messageCard || continue
                        val v = player.calculateRemoveCardValue(e.whoseTurn, p, c)
                        if (v > value) {
                            value = v
                            playerAndCard = PlayerAndCard(p, c)
                        }
                    }
                }
            }
            target.deleteMessageCard(e.messageCard.id)
            playerAndCard ?: return false
            GameExecutor.post(player.game!!, {
                skill.executeProtocol(player.game!!, player, skillMiaoBiQiaoBianATos {
                    cardId = playerAndCard.card.id
                    targetPlayerId = player.getAlternativeLocation(playerAndCard.player.location)
                })
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}
