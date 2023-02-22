package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.*
import com.fengsheng.phase.MainPhaseIdleimport

com.fengsheng.protos.Common.cardimport com.fengsheng.protos.Common.colorimport com.fengsheng.protos.Roleimport com.fengsheng.protos.Role.skill_jiao_ji_a_tocimport com.fengsheng.protos.Role.skill_jiao_ji_b_tocimport com.fengsheng.skill.JiaoJi.executeJiaoJiimport com.google.protobuf.GeneratedMessageV3import org.apache.log4j.Loggerimport java.util.*import java.util.concurrent.*

/**
 * 裴玲技能【交际】：出牌阶段限一次，你可以抽取一名角色的最多两张手牌。然后将等量手牌交给该角色。你每收集一张黑色情报，便可以少交一张牌。
 */
class JiaoJi : AbstractSkill(), ActiveSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.JIAO_JI
    }

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (g.fsm !is MainPhaseIdle || r !== fsm.player) {
            log.error("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            log.error("[交际]一回合只能发动一次")
            return
        }
        val pb = message as Role.skill_jiao_ji_a_tos
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
        if (target.cards.isEmpty()) {
            log.error("目标没有手牌")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        val random: Random = ThreadLocalRandom.current()
        val cardList: MutableList<Card> = ArrayList()
        var i = 0
        while (i < 2 && !target.cards.isEmpty()) {
            val handCards = target.cards.values.toTypedArray()
            cardList.add(target.deleteCard(handCards[random.nextInt(handCards.size)].id))
            i++
        }
        val cards = cardList.toTypedArray()
        log.info(r.toString() + "对" + target + "发动了[交际]，抽取了" + Arrays.toString(cards))
        for (card in cards) {
            target.deleteCard(card.id)
            r.addCard(card)
        }
        var black = 0
        for (card in r.messageCards.values) {
            if (card.colors.contains(color.Black)) black++
        }
        val needReturnCount = Math.max(0, cards.size - black)
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_jiao_ji_a_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(r.location())
                builder.targetPlayerId = p.getAlternativeLocation(target.location())
                if (p === r || p === target) {
                    for (card in cards) builder.addCards(card.toPbCard())
                } else {
                    builder.unknownCardCount = cards.size
                }
                if (needReturnCount > 0) {
                    builder.waitingSecond = 15
                    if (p === r) {
                        val seq2: Int = p.seq
                        builder.seq = seq2
                        p.setTimeout(GameExecutor.Companion.post(g, Runnable {
                            if (p.checkSeq(seq2)) {
                                val builder2 = Role.skill_jiao_ji_b_tos.newBuilder().setSeq(seq2)
                                var i = 0
                                for (c in r.getCards().values) {
                                    if (i >= needReturnCount) break
                                    builder2.addCardIds(c.id)
                                    i++
                                }
                                g.tryContinueResolveProtocol(r, builder2.build())
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS))
                    }
                }
                p.send(builder.build())
            }
        }
        if (needReturnCount == 0) {
            g.continueResolve()
            return
        }
        if (r is RobotPlayer) {
            GameExecutor.Companion.post(g, Runnable {
                val builder2 = Role.skill_jiao_ji_b_tos.newBuilder()
                var i = 0
                for (c in r.getCards().values) {
                    if (i >= needReturnCount) break
                    builder2.addCardIds(c.id)
                    i++
                }
                g.tryContinueResolveProtocol(r, builder2.build())
            }, 2, TimeUnit.SECONDS)
        }
        g.resolve(executeJiaoJi(fsm, target, needReturnCount))
    }

    private class executeJiaoJi(fsm: MainPhaseIdle, target: Player, needReturnCount: Int) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.player) {
                log.error("不是你发技能的时机")
                return null
            }
            if (message !is Role.skill_jiao_ji_b_tos) {
                log.error("错误的协议")
                return null
            }
            if (message.cardIdsCount != needReturnCount) {
                log.error("卡牌数量不正确，需要返还：" + needReturnCount + "，实际返还：" + message.cardIdsCount)
                return null
            }
            val r = fsm.player
            val g = r.game
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + message.seq)
                return null
            }
            val cards = arrayOfNulls<Card>(needReturnCount)
            for (i in 0 until needReturnCount) {
                val card = r.findCard(message.getCardIds(i))
                if (card == null) {
                    log.error("没有这张卡")
                    return null
                }
                cards[i] = card
            }
            r.incrSeq()
            log.info(r.toString() + "将" + Arrays.toString(cards) + "还给" + target)
            for (card in cards) {
                r.deleteCard(card.getId())
                target.addCard(card)
            }
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jiao_ji_b_toc.newBuilder()
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
            return ResolveResult(fsm, true)
        }

        val fsm: MainPhaseIdle
        val target: Player
        val needReturnCount: Int

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
        }

        companion object {
            private val log = Logger.getLogger(executeJiaoJi::class.java)
        }
    }

    companion object {
        private val log = Logger.getLogger(JiaoJi::class.java)
        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.player
            if (player.getSkillUseCount(SkillId.JIAO_JI) > 0) return false
            val players: MutableList<Player> = ArrayList()
            for (p in player.game.players) {
                if (p !== player && p.isAlive && !p.cards.isEmpty()) players.add(p)
            }
            if (players.isEmpty()) return false
            val target = players[ThreadLocalRandom.current().nextInt(players.size)]
            GameExecutor.Companion.post(player.game, Runnable {
                skill.executeProtocol(
                    player.game, player, Role.skill_jiao_ji_a_tos.newBuilder()
                        .setTargetPlayerId(player.getAlternativeLocation(target.location())).build()
                )
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}