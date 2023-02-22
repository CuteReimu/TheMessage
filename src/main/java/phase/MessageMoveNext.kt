package com.fengsheng.phase

import com.fengsheng.*
import com.fengsheng.protos.Common.cardimport

com.fengsheng.protos.Common.directionimport com.fengsheng.protos.Fengsheng.notify_phase_toc com.fengsheng.protos.Common
import java.util.concurrent.LinkedBlockingQueue
import io.netty.util.HashedWheelTimerimport

org.apache.log4j.Logger
/**
 * 情报传递阶段，情报移到下一个人
 *
 * @param sendPhase 原先那个人的 [SendPhaseIdle] （不是下一个人的）
 */
class MessageMoveNext(sendPhase: SendPhaseIdle) : Fsm {
    override fun resolve(): ResolveResult? {
        if (sendPhase.dir == direction.Up) {
            return if (sendPhase.whoseTurn.isAlive) {
                sendPhase.inFrontOfWhom = sendPhase.whoseTurn
                log.info("情报到达" + sendPhase.inFrontOfWhom + "面前")
                ResolveResult(sendPhase, true)
            } else {
                nextTurn()
            }
        } else {
            val players = sendPhase.whoseTurn.game.players
            var inFrontOfWhom = sendPhase.inFrontOfWhom.location()
            while (true) {
                inFrontOfWhom =
                    if (sendPhase.dir == direction.Left) (inFrontOfWhom + players.size - 1) % players.size else (inFrontOfWhom + 1) % players.size
                sendPhase.inFrontOfWhom = players[inFrontOfWhom]
                if (sendPhase.inFrontOfWhom.isAlive) {
                    log.info("情报到达" + sendPhase.inFrontOfWhom + "面前")
                    return ResolveResult(sendPhase, true)
                } else if (sendPhase.whoseTurn === sendPhase.inFrontOfWhom) {
                    return nextTurn()
                }
            }
        }
    }

    private fun nextTurn(): ResolveResult {
        sendPhase.inFrontOfWhom.game.deck.discard(sendPhase.messageCard)
        if (!sendPhase.isMessageCardFaceUp) {
            val players = sendPhase.whoseTurn.game.players
            for (player in players) {
                if (player is HumanPlayer) {
                    val builder = notify_phase_toc.newBuilder()
                    builder.currentPlayerId = player.getAlternativeLocation(sendPhase.whoseTurn.location())
                    builder.currentPhase = Common.phase.Send_Phase
                    builder.messagePlayerId = player.getAlternativeLocation(sendPhase.inFrontOfWhom.location())
                    builder.messageCardDir = sendPhase.dir
                    builder.messageCard = sendPhase.messageCard.toPbCard()
                    builder.waitingPlayerId = player.getAlternativeLocation(sendPhase.inFrontOfWhom.location())
                    player.send(builder.build())
                }
            }
        }
        return ResolveResult(NextTurn(sendPhase.whoseTurn), true)
    }

    val sendPhase: SendPhaseIdle

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
    }

    companion object {
        private val log = Logger.getLogger(MessageMoveNext::class.java)
    }
}