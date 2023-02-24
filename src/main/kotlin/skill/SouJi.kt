package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.*
import com.fengsheng.phase.FightPhaseIdleimport

com.fengsheng.phase.NextTurnimport com.fengsheng.protos.Common.cardimport com.fengsheng.protos.Common.colorimport com.fengsheng.protos.Roleimport com.fengsheng.protos.Role.skill_sou_ji_a_tocimport com.fengsheng.protos.Role.skill_sou_ji_b_tocimport com.fengsheng.skill.SouJi.executeSouJiimport com.google.protobuf.GeneratedMessageV3import org.apache.log4j.Loggerimport java.util.*import java.util.concurrent.*

/**
 * 李醒技能【搜辑】：争夺阶段，你可以翻开此角色牌，然后查看一名角色的手牌和待收情报，并且你可以选择其中任意张黑色牌，展示并加入你的手牌。
 */
class SouJi : AbstractSkill(), ActiveSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.SOU_JI
    }

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (g.fsm !is FightPhaseIdle || r !== fsm.whoseFightTurn) {
            log.error("现在不是发动[搜辑]的时机")
            return
        }
        if (r.isRoleFaceUp) {
            log.error("你现在正面朝上，不能发动[搜辑]")
            return
        }
        val pb = message as Role.skill_sou_ji_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + pb.seq)
            return
        }
        if (pb.targetPlayerId < 0 || pb.targetPlayerId >= g.players.size) {
            log.error("目标错误")
            return
        }
        if (pb.targetPlayerId == 0) {
            log.error("不能以自己为目标")
            return
        }
        val target = g.players[r.getAbstractLocation(pb.targetPlayerId)]
        if (!target.isAlive) {
            log.error("目标已死亡")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        g.deck.discard(fsm.messageCard)
        g.resolve(executeSouJi(fsm, r, target))
    }

    private class executeSouJi(fsm: FightPhaseIdle, r: Player, target: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game
            log.info(r.toString() + "对" + target + "发动了[搜辑]")
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_sou_ji_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location())
                    builder.targetPlayerId = p.getAlternativeLocation(target.location())
                    builder.waitingSecond = 20
                    if (p === r) {
                        for (card in target.cards.values) builder.addCards(card.toPbCard())
                        builder.messageCard = fsm.messageCard.toPbCard()
                        val seq2: Int = p.seq
                        builder.seq = seq2
                        p.setTimeout(
                            GameExecutor.Companion.post(
                                g,
                                Runnable {
                                    if (p.checkSeq(seq2)) g.tryContinueResolveProtocol(
                                        r,
                                        Role.skill_sou_ji_b_tos.newBuilder().setSeq(seq2).build()
                                    )
                                },
                                p.getWaitSeconds(builder.waitingSecond + 2).toLong(),
                                TimeUnit.SECONDS
                            )
                        )
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.Companion.post(g, Runnable {
                    val builder = Role.skill_sou_ji_b_tos.newBuilder()
                    for (card in target.cards.values) {
                        if (card.colors.contains(color.Black)) builder.addCardIds(card.id)
                    }
                    if (fsm.messageCard.colors.contains(color.Black)) builder.messageCard = true
                    g.tryContinueResolveProtocol(r, builder.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                log.error("不是你发技能的时机")
                return null
            }
            if (message !is Role.skill_sou_ji_b_tos) {
                log.error("错误的协议")
                return null
            }
            val g = r.game
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + message.seq)
                return null
            }
            val cards = arrayOfNulls<Card>(message.cardIdsCount)
            for (i in cards.indices) {
                val card = target.findCard(message.getCardIds(i))
                if (card == null) {
                    log.error("没有这张牌")
                    return null
                }
                if (!card.colors.contains(color.Black)) {
                    log.error("这张牌不是黑色的")
                    return null
                }
                cards[i] = card
            }
            if (message.messageCard && !fsm.messageCard.colors.contains(color.Black)) {
                log.error("待收情报不是黑色的")
                return null
            }
            r.incrSeq()
            if (cards.size > 0) {
                log.info(r.toString() + "将" + target + "的" + Arrays.toString(cards) + "收归手牌")
                for (card in cards) target.deleteCard(card.getId())
                r.addCard(*cards)
            }
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_sou_ji_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location())
                    builder.targetPlayerId = p.getAlternativeLocation(target.location())
                    for (card in cards) builder.addCards(card!!.toPbCard())
                    if (message.messageCard) builder.messageCard = fsm.messageCard.toPbCard()
                    p.send(builder.build())
                }
            }
            if (message.messageCard) {
                log.info(r.toString() + "将待收情报" + fsm.messageCard + "收归手牌，回合结束")
                r.addCard(fsm.messageCard)
                return ResolveResult(NextTurn(fsm.whoseTurn), true)
            }
            fsm.whoseFightTurn = fsm.inFrontOfWhom
            return ResolveResult(fsm, true)
        }

        val fsm: FightPhaseIdle
        val r: Player
        val target: Player

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
        }

        companion object {
            private val log = Logger.getLogger(executeSouJi::class.java)
        }
    }

    companion object {
        private val log = Logger.getLogger(SouJi::class.java)
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseFightTurn
            if (player.isRoleFaceUp) return false
            val players: MutableList<Player> = ArrayList()
            for (p in player.game.players) {
                if (p !== player && p.isAlive && (!p.cards.isEmpty() || !p.messageCards.isEmpty())) {
                    players.add(p)
                }
            }
            if (players.isEmpty()) return false
            if (ThreadLocalRandom.current().nextInt(players.size + 1) != 0) return false
            val p = players[ThreadLocalRandom.current().nextInt(players.size)]
            GameExecutor.Companion.post(player.game, Runnable {
                skill.executeProtocol(
                    player.game,
                    player,
                    Role.skill_sou_ji_a_tos.newBuilder().setTargetPlayerId(player.getAlternativeLocation(p.location()))
                        .build()
                )
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}