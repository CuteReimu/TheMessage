package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.*
import com.fengsheng.phase.MainPhaseIdleimport

com.fengsheng.protos.Common.cardimport com.fengsheng.protos.Roleimport com.fengsheng.protos.Role.skill_ji_ban_a_tocimport com.fengsheng.protos.Role.skill_ji_ban_b_tocimport com.fengsheng.skill.JiBan.executeJiBanimport com.google.protobuf.GeneratedMessageV3import org.apache.log4j.Loggerimport java.util.*import java.util.concurrent.*

/**
 * SP顾小梦技能【羁绊】：出牌阶段限一次，可以摸两张牌，然后将至少一张手牌交给另一名角色。
 */
class JiBan : AbstractSkill(), ActiveSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.JI_BAN
    }

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (g.fsm !is MainPhaseIdle || r !== fsm.player) {
            log.error("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            log.error("[羁绊]一回合只能发动一次")
            return
        }
        val pb = message as Role.skill_ji_ban_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + pb.seq)
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.resolve(executeJiBan(r))
    }

    private class executeJiBan(r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game
            log.info(r.toString() + "发动了[羁绊]")
            r.draw(2)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_ji_ban_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location())
                    builder.waitingSecond = 15
                    if (p === r) {
                        val seq2: Int = p.seq
                        builder.seq = seq2
                        p.setTimeout(
                            GameExecutor.Companion.post(
                                g,
                                Runnable { if (p.checkSeq(seq2)) autoSelect(seq2) },
                                p.getWaitSeconds(builder.waitingSecond + 2).toLong(),
                                TimeUnit.SECONDS
                            )
                        )
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer) GameExecutor.Companion.post(g, Runnable { autoSelect(0) }, 2, TimeUnit.SECONDS)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                log.error("不是你发技能的时机")
                return null
            }
            if (message !is Role.skill_ji_ban_b_tos) {
                log.error("错误的协议")
                return null
            }
            val g = r.game
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + message.seq)
                return null
            }
            if (message.cardIdsCount == 0) {
                log.error("至少需要选择一张卡牌")
                return null
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                log.error("目标错误")
                return null
            }
            if (message.targetPlayerId == 0) {
                log.error("不能以自己为目标")
                return null
            }
            val target = g.players[r.getAbstractLocation(message.targetPlayerId)]
            if (!target.isAlive) {
                log.error("目标已死亡")
                return null
            }
            val cards = arrayOfNulls<Card>(message.cardIdsCount)
            for (i in 0 until message.cardIdsCount) {
                val card = r.findCard(message.getCardIds(i))
                if (card == null) {
                    log.error("没有这张卡")
                    return null
                }
                cards[i] = card
            }
            r.incrSeq()
            log.info(r.toString() + "将" + Arrays.toString(cards) + "交给" + target)
            for (card in cards) r.deleteCard(card.getId())
            target.addCard(*cards)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_ji_ban_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location())
                    builder.targetPlayerId = p.getAlternativeLocation(target.location())
                    if (p === r || p === target) {
                        for (card in cards) builder.addCards(card!!.toPbCard())
                    } else {
                        builder.unknownCardCount = cards.size
                    }
                    p.send(builder.build())
                }
            }
            return ResolveResult(MainPhaseIdle(r), true)
        }

        private fun autoSelect(seq: Int) {
            val players: MutableList<Player> = ArrayList()
            for (player in r.game.players) {
                if (player.isAlive && player !== r) players.add(player)
            }
            val player = players[ThreadLocalRandom.current().nextInt(players.size)]
            for (card in r.cards.values) {
                r.game.tryContinueResolveProtocol(
                    r, Role.skill_ji_ban_b_tos.newBuilder().addCardIds(card.id).setSeq(seq)
                        .setTargetPlayerId(r.getAlternativeLocation(player.location())).build()
                )
                break
            }
        }

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
        }

        companion object {
            private val log = Logger.getLogger(executeJiBan::class.java)
        }
    }

    companion object {
        private val log = Logger.getLogger(JiBan::class.java)
        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            if (e.player.getSkillUseCount(SkillId.JI_BAN) > 0) return false
            GameExecutor.Companion.post(e.player.game, Runnable {
                skill.executeProtocol(
                    e.player.game, e.player, Role.skill_ji_ban_a_tos.getDefaultInstance()
                )
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}