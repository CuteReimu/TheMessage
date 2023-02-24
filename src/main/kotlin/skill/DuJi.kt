package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.*
import com.fengsheng.phase.CheckWinimport

com.fengsheng.phase.FightPhaseIdleimport com.fengsheng.protos.Common.cardimport com.fengsheng.protos.Common.colorimport com.fengsheng.protos.Roleimport com.fengsheng.protos.Role.*import com.fengsheng.skill.DuJi.executeDuJiAimport

com.fengsheng.skill.DuJi.executeDuJiBimport com.google.protobuf.GeneratedMessageV3import org.apache.log4j.Loggerimport java.util.*import java.util.concurrent.*

/**
 * 白昆山技能【毒计】：争夺阶段，你可以翻开此角色牌，然后指定两名其他角色，令他们相互抽取对方的一张手牌并展示之，你将展示的牌加入你的手牌，若展示的是黑色牌，你可以改为令抽取者选择一项：
 *  * 将其置入自己的情报区 * 将其置入对方的情报区
 */
class DuJi : AbstractSkill(), ActiveSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.DU_JI
    }

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (g.fsm !is FightPhaseIdle || r !== fsm.whoseFightTurn) {
            log.error("现在不是发动[毒计]的时机")
            return
        }
        if (r.isRoleFaceUp) {
            log.error("你现在正面朝上，不能发动[毒计]")
            return
        }
        val pb = message as Role.skill_du_ji_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + pb.seq)
            return
        }
        if (pb.targetPlayerIdsCount != 2) {
            log.error("[毒计]必须选择两名角色为目标")
            return
        }
        val idx1 = pb.getTargetPlayerIds(0)
        val idx2 = pb.getTargetPlayerIds(1)
        if (idx1 < 0 || idx1 >= g.players.size || idx2 < 0 || idx2 >= g.players.size) {
            log.error("目标错误")
            return
        }
        if (idx1 == 0 || idx2 == 0) {
            log.error("不能以自己为目标")
            return
        }
        val target1 = g.players[r.getAbstractLocation(idx1)]
        val target2 = g.players[r.getAbstractLocation(idx2)]
        if (!target1.isAlive || !target2.isAlive) {
            log.error("目标已死亡")
            return
        }
        if (target1.cards.isEmpty() || target2.cards.isEmpty()) {
            log.error("目标没有手牌")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        val cards1 = target1.cards.values.toTypedArray()
        val cards2 = target2.cards.values.toTypedArray()
        val card1 = cards1[ThreadLocalRandom.current().nextInt(cards1.size)]
        val card2 = cards2[ThreadLocalRandom.current().nextInt(cards2.size)]
        log.info(r.toString() + "发动了[毒计]，抽取了" + target1 + "的" + card1 + "和" + target2 + "的" + card2)
        target1.deleteCard(card1.id)
        target2.deleteCard(card2.id)
        r.addCard(card1, card2)
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_du_ji_a_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(r.location())
                builder.addTargetPlayerIds(p.getAlternativeLocation(target1.location()))
                builder.addTargetPlayerIds(p.getAlternativeLocation(target2.location()))
                builder.addCards(card1.toPbCard())
                builder.addCards(card2.toPbCard())
                p.send(builder.build())
            }
        }
        fsm.whoseFightTurn = fsm.inFrontOfWhom
        val twoPlayersAndCards: MutableList<TwoPlayersAndCard> = ArrayList()
        if (card1.colors.contains(color.Black)) twoPlayersAndCards.add(TwoPlayersAndCard(target1, target2, card1))
        if (card2.colors.contains(color.Black)) twoPlayersAndCards.add(TwoPlayersAndCard(target2, target1, card2))
        g.resolve(executeDuJiA(CheckWin(fsm.whoseTurn, fsm), r, twoPlayersAndCards))
    }

    private class executeDuJiA(fsm: CheckWin, r: Player, playerAndCards: MutableList<TwoPlayersAndCard>) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            if (playerAndCards.isEmpty()) return ResolveResult(fsm, true)
            val g = r.game
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_wait_for_du_ji_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location())
                    for (twoPlayersAndCard in playerAndCards) {
                        builder.addTargetPlayerIds(p.getAlternativeLocation(twoPlayersAndCard.waitingPlayer.location()))
                        builder.addCardIds(twoPlayersAndCard.card.id)
                    }
                    builder.waitingSecond = 15
                    if (p === r) {
                        val seq2: Int = p.seq
                        builder.seq = seq2
                        p.setTimeout(GameExecutor.Companion.post(g, Runnable {
                            if (p.checkSeq(seq2)) {
                                g.tryContinueResolveProtocol(
                                    r, Role.skill_du_ji_b_tos.newBuilder()
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
                    if (!playerAndCards.isEmpty()) g.tryContinueResolveProtocol(
                        r, Role.skill_du_ji_b_tos.newBuilder().setEnable(true)
                            .setCardId(playerAndCards[0].card.id).build()
                    ) else g.tryContinueResolveProtocol(r, Role.skill_du_ji_b_tos.newBuilder().setEnable(false).build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                log.error("不是你发技能的时机")
                return null
            }
            if (message !is Role.skill_du_ji_b_tos) {
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
                        skill_du_ji_b_toc.newBuilder().setPlayerId(p.getAlternativeLocation(r.location()))
                            .setEnable(false).build()
                    )
                }
                return ResolveResult(fsm, true)
            }
            var selection: TwoPlayersAndCard? = null
            val it = playerAndCards.iterator()
            while (it.hasNext()) {
                val twoPlayersAndCard = it.next()
                if (twoPlayersAndCard.card.id == message.cardId) {
                    selection = twoPlayersAndCard
                    it.remove()
                    break
                }
            }
            if (selection == null) {
                log.error("目标卡牌不存在")
                return null
            }
            r.incrSeq()
            return ResolveResult(executeDuJiB(this, selection), true)
        }

        val fsm: CheckWin
        val r: Player
        val playerAndCards: MutableList<TwoPlayersAndCard>

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
        }

        companion object {
            private val log = Logger.getLogger(executeDuJiA::class.java)
        }
    }

    private class executeDuJiB(fsm: executeDuJiA, selection: TwoPlayersAndCard) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            log.info("等待" + selection.waitingPlayer + "对" + selection.card + "进行选择")
            val g = selection.waitingPlayer.game
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_du_ji_b_toc.newBuilder().setEnable(true)
                    builder.playerId = p.getAlternativeLocation(fsm.r.location())
                    builder.waitingPlayerId = p.getAlternativeLocation(selection.waitingPlayer.location())
                    builder.targetPlayerId = p.getAlternativeLocation(selection.fromPlayer.location())
                    builder.card = selection.card.toPbCard()
                    builder.waitingSecond = 15
                    if (p === selection.waitingPlayer) {
                        val seq2: Int = p.seq
                        builder.seq = seq2
                        p.setTimeout(GameExecutor.Companion.post(g, Runnable {
                            if (p.checkSeq(seq2)) g.tryContinueResolveProtocol(
                                selection.waitingPlayer, Role.skill_du_ji_c_tos.newBuilder()
                                    .setInFrontOfMe(false).setSeq(seq2).build()
                            )
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS))
                    }
                    p.send(builder.build())
                }
            }
            if (selection.waitingPlayer is RobotPlayer) {
                GameExecutor.Companion.post(g, Runnable {
                    g.tryContinueResolveProtocol(
                        selection.waitingPlayer, Role.skill_du_ji_c_tos.newBuilder()
                            .setInFrontOfMe(false).build()
                    )
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            val r = selection.waitingPlayer
            if (player !== r) {
                log.error("当前没有轮到你结算[毒计]")
                return null
            }
            if (message !is Role.skill_du_ji_c_tos) {
                log.error("错误的协议")
                return null
            }
            val g = r.game
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + message.seq)
                return null
            }
            r.incrSeq()
            val target = if (message.inFrontOfMe) selection.waitingPlayer else selection.fromPlayer
            val card = selection.card
            log.info(r.toString() + "选择将" + card + "放在" + target + "面前")
            fsm.r.deleteCard(card.id)
            target.addMessageCard(card)
            fsm.fsm.receiveOrder.addPlayerIfHasThreeBlack(target)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_du_ji_c_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(fsm.r.location())
                    builder.waitingPlayerId = p.getAlternativeLocation(r.location())
                    builder.targetPlayerId = p.getAlternativeLocation(target.location())
                    builder.card = card.toPbCard()
                    p.send(builder.build())
                }
            }
            return ResolveResult(fsm, true)
        }

        val fsm: executeDuJiA
        val selection: TwoPlayersAndCard

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
        }

        companion object {
            private val log = Logger.getLogger(executeDuJiB::class.java)
        }
    }

    private class TwoPlayersAndCard(fromPlayer: Player, waitingPlayer: Player, val card: Card) {
        val fromPlayer: Player
        val waitingPlayer: Player

        init {
            this.sendPhase = sendPhase
            this.r = r
            this.target = target
            card = card
            this.wantType = wantType
            this.r = r
            this.target = target
            card = card
            this.player = player
            card = card
            card = card
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
            card = card
        }
    }

    companion object {
        private val log = Logger.getLogger(DuJi::class.java)
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseFightTurn
            if (player.isRoleFaceUp) return false
            val players: MutableList<Player> = ArrayList()
            for (p in player.game.players) {
                if (p !== player && p.isAlive && !p.cards.isEmpty()) players.add(p)
            }
            val playerCount = players.size
            if (playerCount < 2) return false
            if (ThreadLocalRandom.current().nextInt(playerCount * playerCount) != 0) return false
            val random: Random = ThreadLocalRandom.current()
            val i = random.nextInt(playerCount)
            var j = random.nextInt(playerCount)
            j = if (i == j) (j + 1) % playerCount else j
            val player1 = players[i]
            val player2 = players[j]
            GameExecutor.Companion.post(player.game, Runnable {
                skill.executeProtocol(
                    player.game, player, Role.skill_du_ji_a_tos.newBuilder()
                        .addTargetPlayerIds(player.getAlternativeLocation(player1.location()))
                        .addTargetPlayerIds(player.getAlternativeLocation(player2.location())).build()
                )
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}