package com.fengsheng.phase

import com.fengsheng.*
import com.fengsheng.protos.Common.card

com.fengsheng.card.*
import java.util.concurrent.LinkedBlockingQueue
import io.netty.util.HashedWheelTimerimport

org.apache.log4j.Logger
/**
 * 选择接收情报时
 *
 * @param whoseTurn           谁的回合（也就是情报传出者）
 * @param messageCard         情报牌
 * @param inFrontOfWhom       情报在谁面前
 * @param isMessageCardFaceUp 情报是否面朝上
 */
class OnChooseReceiveCard(whoseTurn: Player, messageCard: Card, inFrontOfWhom: Player, isMessageCardFaceUp: Boolean) :
    Fsm {
    override fun resolve(): ResolveResult? {
        val result = inFrontOfWhom.game.dealListeningSkill()
        if (result != null) return result
        log.info(inFrontOfWhom.toString() + "选择接收情报")
        for (p in whoseTurn.game.players) p.notifyChooseReceiveCard(inFrontOfWhom)
        return ResolveResult(
            FightPhaseIdle(whoseTurn, messageCard, inFrontOfWhom, inFrontOfWhom, isMessageCardFaceUp),
            true
        )
    }

    val whoseTurn: Player
    val messageCard: Card
    val inFrontOfWhom: Player
    val isMessageCardFaceUp: Boolean

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
    }

    companion object {
        private val log = Logger.getLogger(OnChooseReceiveCard::class.java)
    }
}