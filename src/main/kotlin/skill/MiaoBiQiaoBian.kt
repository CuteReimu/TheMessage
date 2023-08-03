package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.card.PlayerAndCard
import com.fengsheng.card.count
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 连鸢技能【妙笔巧辩】：争夺阶段，你可以翻开此角色牌，然后从所有角色的情报区选择合计至多两张不含有相同颜色的情报，将其加入你的手牌。
 */
class MiaoBiQiaoBian : AbstractSkill(), ActiveSkill {
    override val skillId = SkillId.MIAO_BI_QIAO_BIAN

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        val fsm = g.fsm as? FightPhaseIdle
        if (r !== fsm?.whoseFightTurn) {
            log.error("现在不是发动[妙笔巧辩]的时机")
            (r as? HumanPlayer)?.sendErrorMessage("现在不是发动[妙笔巧辩]的时机")
            return
        }
        if (r.roleFaceUp) {
            log.error("你现在正面朝上，不能发动[妙笔巧辩]")
            (r as? HumanPlayer)?.sendErrorMessage("你现在正面朝上，不能发动[妙笔巧辩]")
            return
        }
        val pb = message as skill_miao_bi_qiao_bian_a_tos
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
            log.error("没有这张牌")
            (r as? HumanPlayer)?.sendErrorMessage("没有这张牌")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        g.resolve(executeMiaoBiQiaoBian(fsm, r, target, card))
    }

    private data class executeMiaoBiQiaoBian(
        val fsm: FightPhaseIdle,
        val r: Player,
        val target1: Player,
        val card1: Card
    ) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game!!
            target1.deleteMessageCard(card1.id)
            r.cards.add(card1)
            log.info("${r}发动了[妙笔巧辩]，拿走了${target1}面前的${card1}")
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_miao_bi_qiao_bian_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target1.location)
                    builder.cardId = card1.id
                    builder.waitingSecond = 15
                    if (p === r) {
                        val seq2: Int = p.seq
                        builder.seq = seq2
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq2)) {
                                val builder2 = skill_miao_bi_qiao_bian_b_tos.newBuilder()
                                builder2.enable = false
                                builder2.seq = seq2
                                g.tryContinueResolveProtocol(r, builder2.build())
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    val playerAndCards = ArrayList<PlayerAndCard>()
                    for (p in g.players) {
                        if (p!!.alive) {
                            for (c in p.messageCards) {
                                if (!card1.hasSameColor(c)) playerAndCards.add(PlayerAndCard(p, c))
                            }
                        }
                    }
                    if (playerAndCards.isEmpty()) {
                        val builder = skill_miao_bi_qiao_bian_b_tos.newBuilder()
                        builder.enable = false
                        g.tryContinueResolveProtocol(r, builder.build())
                    } else {
                        val playerAndCard = playerAndCards[Random.nextInt(playerAndCards.size)]
                        val builder = skill_miao_bi_qiao_bian_b_tos.newBuilder()
                        builder.enable = true
                        builder.targetPlayerId = r.getAlternativeLocation(playerAndCard.player.location)
                        builder.cardId = playerAndCard.card.id
                        g.tryContinueResolveProtocol(r, builder.build())
                    }
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_miao_bi_qiao_bian_b_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            if (!message.enable) {
                r.incrSeq()
                return ResolveResult(fsm, true)
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                log.error("目标错误")
                (player as? HumanPlayer)?.sendErrorMessage("目标错误")
                return null
            }
            val target2 = g.players[r.getAbstractLocation(message.targetPlayerId)]!!
            if (!target2.alive) {
                log.error("目标已死亡")
                (player as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                return null
            }
            val card2 = target2.findMessageCard(message.cardId)
            if (card2 == null) {
                log.error("没有这张牌")
                (player as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                return null
            }
            if (card2.hasSameColor(card1)) {
                log.error("两张牌含有相同颜色")
                (player as? HumanPlayer)?.sendErrorMessage("两张牌含有相同颜色")
                return null
            }
            r.incrSeq()
            log.info("${r}拿走了${target2}面前的$card2")
            target2.deleteMessageCard(card2.id)
            r.cards.add(card2)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_miao_bi_qiao_bian_b_toc.newBuilder()
                    builder.cardId = card2.id
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target2.location)
                    p.send(builder.build())
                }
            }
            return ResolveResult(fsm.copy(whoseFightTurn = fsm.inFrontOfWhom), true)
        }

        companion object {
            private val log = Logger.getLogger(executeMiaoBiQiaoBian::class.java)
        }
    }

    companion object {
        private val log = Logger.getLogger(MiaoBiQiaoBian::class.java)
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseFightTurn
            if (player.roleFaceUp) return false
            val playerAndCard = player.game!!.players.find {
                it!!.alive && player.isEnemy(it) && it.identity != color.Black && it.messageCards.count(it.identity) >= 2
            }?.run {
                val card = messageCards.filter { identity in it.colors }.run {
                    find { it.colors.size == 1 } ?: first() // 优先找纯色
                }
                PlayerAndCard(this, card)
            } ?: return false
            val builder = skill_miao_bi_qiao_bian_a_tos.newBuilder()
            builder.cardId = playerAndCard.card.id
            builder.targetPlayerId = player.getAlternativeLocation(playerAndCard.player.location)
            GameExecutor.post(player.game!!, {
                skill.executeProtocol(player.game!!, player, builder.build())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}