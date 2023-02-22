package com.fengsheng.phase

import com.fengsheng.*
import com.fengsheng.protos.Common.card

com.fengsheng.card.*
import java.util.concurrent.LinkedBlockingQueue
import io.netty.util.HashedWheelTimerimport

org.apache.log4j.Logger
/**
 * 接收阶段（确定接收后，即将发动接收时的技能）
 *
 * @param whoseTurn     谁的回合
 * @param messageCard   情报牌
 * @param inFrontOfWhom 情报在谁面前
 */
class ReceivePhase(whoseTurn: Player, messageCard: Card, inFrontOfWhom: Player) : Fsm {
    override fun resolve(): ResolveResult? {
        val player = inFrontOfWhom
        if (player.isAlive) {
            player.addMessageCard(messageCard)
            log.info(player.toString() + "成功接收情报")
            for (p in player.game.players) p.notifyReceivePhase()
            val next = ReceivePhaseSenderSkill(whoseTurn, messageCard, inFrontOfWhom)
            next.receiveOrder.addPlayerIfHasThreeBlack(inFrontOfWhom)
            return ResolveResult(next, true)
        }
        player.game.deck.discard(messageCard)
        for (p in player.game.players) p.notifyReceivePhase()
        return ResolveResult(NextTurn(whoseTurn), true)
    }

    val whoseTurn: Player
    val messageCard: Card
    val inFrontOfWhom: Player

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
    }

    companion object {
        private val log = Logger.getLogger(ReceivePhase::class.java)
    }
}