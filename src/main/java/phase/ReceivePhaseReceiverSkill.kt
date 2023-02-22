package com.fengsheng.phase

import com.fengsheng.*
import com.fengsheng.card.*
import com.fengsheng.protos.Common.card

/**
 * 接收情报时，接收者的技能
 *
 * @param whoseTurn     谁的回合
 * @param messageCard   情报牌
 * @param receiveOrder  接收情报牌的顺序（也就是后续结算死亡的顺序）
 * @param inFrontOfWhom 情报在谁面前
 */
class ReceivePhaseReceiverSkill(
    whoseTurn: Player,
    messageCard: Card,
    receiveOrder: ReceiveOrder,
    inFrontOfWhom: Player
) : Fsm {
    override fun resolve(): ResolveResult? {
        val result = inFrontOfWhom.game.dealListeningSkill()
        return result ?: ResolveResult(CheckWin(whoseTurn, receiveOrder, NextTurn(whoseTurn)), true)
    }

    override fun toString(): String {
        return whoseTurn.toString() + "的回合，" + inFrontOfWhom + "成功接收情报"
    }

    val whoseTurn: Player
    val messageCard: Card
    val receiveOrder: ReceiveOrder
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
    }
}