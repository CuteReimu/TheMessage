package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.*
import com.fengsheng.phase.FightPhaseIdleimport

com.fengsheng.protos.Common.cardimport com.fengsheng.protos.Roleimport com.fengsheng.protos.Role.skill_guang_fa_bao_b_tocimport com.fengsheng.protos.Role.skill_wait_for_guang_fa_bao_b_tocimport com.fengsheng.skill.GuangFaBao.executeGuangFaBaoimport com.google.protobuf.GeneratedMessageV3import org.apache.log4j.Loggerimport java.util.*import java.util.concurrent.*

/**
 * 小九技能【广发报】：争夺阶段，你可以翻开此角色牌，然后摸三张牌，并且你可以将你的任意张手牌置入任意名角色的情报区。你不能通过此技能让任何角色收集三张或更多的同色情报。
 */
class GuangFaBao : AbstractSkill(), ActiveSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.GUANG_FA_BAO
    }

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (g.fsm !is FightPhaseIdle || r !== fsm.whoseFightTurn) {
            log.error("现在不是发动[广发报]的时机")
            return
        }
        if (r.isRoleFaceUp) {
            log.error("你现在正面朝上，不能发动[广发报]")
            return
        }
        val pb = message as Role.skill_guang_fa_bao_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + pb.seq)
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        log.info(r.toString() + "发动了[广发报]")
        for (p in g.players) {
            (p as? HumanPlayer)?.send(
                Role.skill_guang_fa_bao_a_toc.newBuilder().setPlayerId(p.getAlternativeLocation(r.location())).build()
            )
        }
        r.draw(3)
        g.resolve(executeGuangFaBao(fsm, r))
    }

    private class executeGuangFaBao(fsm: FightPhaseIdle, r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_wait_for_guang_fa_bao_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location())
                    builder.waitingSecond = 15
                    if (p === r) {
                        val seq2: Int = p.seq
                        builder.seq = seq2
                        p.setTimeout(GameExecutor.Companion.post(g, Runnable {
                            if (p.checkSeq(seq2)) {
                                g.tryContinueResolveProtocol(
                                    r, Role.skill_guang_fa_bao_b_tos.newBuilder()
                                        .setEnable(false).setSeq(seq2).build()
                                )
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS))
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.Companion.post(g, Runnable {
                    for (p in g.players) {
                        val cardIds: MutableList<Int> = ArrayList()
                        val cards: MutableList<Card> = ArrayList()
                        for (card in r.getCards().values) {
                            cards.add(card)
                            if (p.checkThreeSameMessageCard(*cards.toTypedArray())) cards.remove(card) else cardIds.add(
                                card.id
                            )
                        }
                        if (!cardIds.isEmpty()) {
                            g.tryContinueResolveProtocol(
                                r, Role.skill_guang_fa_bao_b_tos.newBuilder().setEnable(true)
                                    .setTargetPlayerId(r.getAlternativeLocation(p.location())).addAllCardIds(cardIds)
                                    .build()
                            )
                            return@post
                        }
                    }
                    g.tryContinueResolveProtocol(r, Role.skill_guang_fa_bao_b_tos.newBuilder().setEnable(false).build())
                }, 1, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                log.error("不是你发技能的时机")
                return null
            }
            if (message !is Role.skill_guang_fa_bao_b_tos) {
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
                for (p in g.players) {
                    (p as? HumanPlayer)?.send(
                        skill_guang_fa_bao_b_toc.newBuilder().setPlayerId(p.getAlternativeLocation(r.location()))
                            .setEnable(false).build()
                    )
                }
                fsm.whoseFightTurn = fsm.inFrontOfWhom
                return ResolveResult(fsm, true)
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                log.error("目标错误")
                return null
            }
            val target = g.players[r.getAbstractLocation(message.targetPlayerId)]
            if (!target.isAlive) {
                log.error("目标已死亡")
                return null
            }
            if (message.cardIdsCount == 0) {
                log.error("enable为true时至少要发一张牌")
                return null
            }
            val cards = arrayOfNulls<Card>(message.cardIdsCount)
            for (i in cards.indices) {
                val card = r.findCard(message.getCardIds(i))
                if (card == null) {
                    log.error("没有这张卡")
                    return null
                }
                cards[i] = card
            }
            if (target.checkThreeSameMessageCard(*cards)) {
                log.error("你不能通过此技能让任何角色收集三张或更多的同色情报")
                return null
            }
            r.incrSeq()
            log.info(r.toString() + "将" + Arrays.toString(cards) + "置于" + target + "的情报区")
            for (card in cards) r.deleteCard(card.getId())
            target.addMessageCard(*cards)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_guang_fa_bao_b_toc.newBuilder().setEnable(true)
                    for (card in cards) builder.addCards(card!!.toPbCard())
                    builder.playerId = p.getAlternativeLocation(r.location())
                    builder.targetPlayerId = p.getAlternativeLocation(target.location())
                    p.send(builder.build())
                }
            }
            if (!r.cards.isEmpty()) return ResolveResult(this, true)
            fsm.whoseFightTurn = fsm.inFrontOfWhom
            return ResolveResult(fsm, true)
        }

        val fsm: FightPhaseIdle
        val r: Player

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
        }

        companion object {
            private val log = Logger.getLogger(executeGuangFaBao::class.java)
        }
    }

    companion object {
        private val log = Logger.getLogger(GuangFaBao::class.java)
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseFightTurn
            if (player.isRoleFaceUp) return false
            if (player.cards.size < 5) return false
            GameExecutor.Companion.post(player.game, Runnable {
                skill.executeProtocol(
                    player.game, player, Role.skill_guang_fa_bao_a_tos.newBuilder().build()
                )
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}