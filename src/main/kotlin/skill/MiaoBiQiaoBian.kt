package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.*
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Common.card
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Role
import com.fengsheng.protos.Role.skill_miao_bi_qiao_bian_a_toc
import com.fengsheng.skill.MiaoBiQiaoBian.executeMiaoBiQiaoBian
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.*

/**
 * 连鸢技能【妙笔巧辩】：争夺阶段，你可以翻开此角色牌，然后从所有角色的情报区选择合计至多两张不含有相同颜色的情报，将其加入你的手牌。
 */
class MiaoBiQiaoBian : AbstractSkill(), ActiveSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.MIAO_BI_QIAO_BIAN
    }

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (g.fsm !is FightPhaseIdle || r !== fsm.whoseFightTurn) {
            log.error("现在不是发动[妙笔巧辩]的时机")
            return
        }
        if (r.isRoleFaceUp) {
            log.error("你现在正面朝上，不能发动[妙笔巧辩]")
            return
        }
        val pb = message as Role.skill_miao_bi_qiao_bian_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + pb.seq)
            return
        }
        if (pb.targetPlayerId < 0 || pb.targetPlayerId >= g.players.size) {
            log.error("目标错误")
            return
        }
        val target = g.players[r.getAbstractLocation(pb.targetPlayerId)]
        if (!target.isAlive) {
            log.error("目标已死亡")
            return
        }
        val card = target.findMessageCard(pb.cardId)
        if (card == null) {
            log.error("没有这张牌")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        g.resolve(executeMiaoBiQiaoBian(fsm, r, target, card))
    }

    private class executeMiaoBiQiaoBian(fsm: FightPhaseIdle, r: Player, target1: Player, card1: Card) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game
            target1.deleteMessageCard(card1.id)
            r.addCard(card1)
            log.info(r.toString() + "发动了[妙笔巧辩]，拿走了" + target1 + "面前的" + card1)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_miao_bi_qiao_bian_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location())
                    builder.targetPlayerId = p.getAlternativeLocation(target1.location())
                    builder.cardId = card1.id
                    builder.waitingSecond = 15
                    if (p === r) {
                        val seq2: Int = p.seq
                        builder.seq = seq2
                        p.setTimeout(GameExecutor.Companion.post(g, Runnable {
                            if (p.checkSeq(seq2)) {
                                g.tryContinueResolveProtocol(
                                    r, Role.skill_miao_bi_qiao_bian_b_tos.newBuilder()
                                        .setEnable(false).setSeq(seq2).build()
                                )
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS))
                    }
                    p.send(builder.build())
                }
            }
            fsm.whoseFightTurn = fsm.inFrontOfWhom
            if (r is RobotPlayer) {
                GameExecutor.Companion.post(g, Runnable {
                    val playerAndCards: MutableList<PlayerAndCard> = ArrayList()
                    for (p in g.players) {
                        if (p.isAlive) {
                            for (c in p.messageCards.values) {
                                if (!card1.hasSameColor(c)) playerAndCards.add(PlayerAndCard(p, c))
                            }
                        }
                    }
                    if (playerAndCards.isEmpty()) {
                        g.tryContinueResolveProtocol(
                            r,
                            Role.skill_miao_bi_qiao_bian_b_tos.newBuilder().setEnable(false).build()
                        )
                    } else {
                        val playerAndCard = playerAndCards[ThreadLocalRandom.current().nextInt(playerAndCards.size)]
                        g.tryContinueResolveProtocol(
                            r, Role.skill_miao_bi_qiao_bian_b_tos.newBuilder().setEnable(true)
                                .setTargetPlayerId(r.getAlternativeLocation(playerAndCard.player.location()))
                                .setCardId(playerAndCard.card.id).build()
                        )
                    }
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                log.error("不是你发技能的时机")
                return null
            }
            if (message !is Role.skill_miao_bi_qiao_bian_b_tos) {
                log.error("错误的协议")
                return null
            }
            val g = r.game
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + message.seq)
                return null
            }
            if (!message.enable) {
                r.incrSeq()
                return ResolveResult(fsm, true)
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                log.error("目标错误")
                return null
            }
            val target2 = g.players[r.getAbstractLocation(message.targetPlayerId)]
            if (!target2.isAlive) {
                log.error("目标已死亡")
                return null
            }
            val card2 = target2.findMessageCard(message.cardId)
            if (card2 == null) {
                log.error("没有这张牌")
                return null
            }
            if (card2.hasSameColor(card1)) {
                log.error("两张牌含有相同颜色")
                return null
            }
            r.incrSeq()
            log.info(r.toString() + "拿走了" + target2 + "面前的" + card2)
            target2.deleteMessageCard(card2.id)
            r.addCard(card2)
            for (p in g.players) {
                (p as? HumanPlayer)?.send(
                    Role.skill_miao_bi_qiao_bian_b_toc.newBuilder().setCardId(card2.id)
                        .setPlayerId(p.getAlternativeLocation(r.location()))
                        .setTargetPlayerId(p.getAlternativeLocation(target2.location())).build()
                )
            }
            return ResolveResult(fsm, true)
        }

        val fsm: FightPhaseIdle
        val r: Player
        val target1: Player
        val card1: Card

        init {
            this.card = card
            this.sendPhase = sendPhase
            this.r = r
            this.target = target
            this.card = card
            this.wantType = wantType
            this.r = r
            this.target = target
            this.card = card
            this.player = player
            this.card = card
            this.card = card
            this.drawCards = drawCards
            this.players = players
            this.mainPhaseIdle = mainPhaseIdle
            this.dieSkill = dieSkill
            this.player = player
            this.player = player
            this.onUseCard = onUseCard
            this.game = game
            this.whoseTurn = whoseTurn
            this.messageCard = messageCard
            this.dir = dir
            this.targetPlayer = targetPlayer
            this.lockedPlayers = lockedPlayers
            this.whoseTurn = whoseTurn
            this.messageCard = messageCard
            this.inFrontOfWhom = inFrontOfWhom
            this.player = player
            this.whoseTurn = whoseTurn
            this.diedQueue = diedQueue
            this.afterDieResolve = afterDieResolve
            this.fightPhase = fightPhase
            this.player = player
            this.sendPhase = sendPhase
            this.dieGiveCard = dieGiveCard
            this.whoseTurn = whoseTurn
            this.messageCard = messageCard
            this.inFrontOfWhom = inFrontOfWhom
            this.isMessageCardFaceUp = isMessageCardFaceUp
            this.waitForChengQing = waitForChengQing
            this.waitForChengQing = waitForChengQing
            this.whoseTurn = whoseTurn
            this.dyingQueue = dyingQueue
            this.diedQueue = diedQueue
            this.afterDieResolve = afterDieResolve
            this.whoseTurn = whoseTurn
            this.messageCard = messageCard
            this.receiveOrder = receiveOrder
            this.inFrontOfWhom = inFrontOfWhom
            this.r = r
            this.fsm = fsm
            this.r = r
            this.playerAndCards = playerAndCards
            this.fsm = fsm
            this.selection = selection
            this.fromPlayer = fromPlayer
            this.waitingPlayer = waitingPlayer
            this.card = card
            this.r = r
            this.r = r
            this.target = target
            this.fsm = fsm
            this.fsm = fsm
            this.r = r
            this.target = target
            this.fsm = fsm
            this.fsm = fsm
            this.target = target
            this.needReturnCount = needReturnCount
            this.fsm = fsm
            this.fsm = fsm
            this.cards = cards
            this.fsm = fsm
            this.fsm = fsm
            this.fsm = fsm
            this.fsm = fsm
            this.fsm = fsm
            this.target = target
            this.fsm = fsm
            this.r = r
            this.target = target
            this.fsm = fsm
            this.r = r
            this.fsm = fsm
            this.fsm = fsm
            this.fsm = fsm
            this.r = r
            this.fsm = fsm
            this.fsm = fsm
            this.r = r
            this.color = color
            this.fsm = fsm
            this.color = color
            this.fsm = fsm
            this.r = r
            this.cards = cards
            this.fsm = fsm
            this.r = r
            this.target = target
            this.card = card
            this.fsm = fsm
            this.r = r
            this.fsm = fsm
            this.r = r
            this.cards = cards
            this.colors = colors
            this.defaultSelection = defaultSelection
            this.fsm = fsm
            this.fsm = fsm
            this.r = r
            this.target1 = target1
            this.card1 = card1
        }

        companion object {
            private val log = Logger.getLogger(executeMiaoBiQiaoBian::class.java)
        }
    }

    companion object {
        private val log = Logger.getLogger(MiaoBiQiaoBian::class.java)
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseFightTurn
            if (player.isRoleFaceUp) return false
            val playerCount = player.game.players.size
            val playerAndCards: MutableList<PlayerAndCard> = ArrayList()
            for (p in player.game.players) {
                if (p.isAlive) {
                    for (c in p.messageCards.values) playerAndCards.add(PlayerAndCard(p, c))
                }
            }
            if (playerAndCards.size < playerCount) return false
            if (ThreadLocalRandom.current().nextInt(playerCount * playerCount) != 0) return false
            val playerAndCard = playerAndCards[ThreadLocalRandom.current().nextInt(playerAndCards.size)]
            GameExecutor.Companion.post(player.game, Runnable {
                skill.executeProtocol(
                    player.game,
                    player,
                    Role.skill_miao_bi_qiao_bian_a_tos.newBuilder().setCardId(playerAndCard.card.id)
                        .setTargetPlayerId(player.getAlternativeLocation(playerAndCard.player.location())).build()
                )
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}