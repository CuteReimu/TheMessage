package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.HumanPlayer
import com.fengsheng.ResolveResult
import com.fengsheng.protos.Common
import com.fengsheng.protos.Common.direction
import com.fengsheng.protos.Fengsheng.notify_phase_toc
import org.apache.logging.log4j.kotlin.logger

/**
 * 情报传递阶段，情报移到下一个人
 *
 * @param sendPhase 原先那个人的 [SendPhaseIdle] （不是下一个人的）
 */
data class MessageMoveNext(val sendPhase: SendPhaseIdle) : Fsm {
    override fun resolve(): ResolveResult {
        if (sendPhase.dir == direction.Up) {
            return if (sendPhase.sender.alive) {
                logger.info("情报到达${sendPhase.sender}面前")
                ResolveResult(sendPhase.copy(inFrontOfWhom = sendPhase.sender), true)
            } else {
                nextTurn()
            }
        } else {
            val players = sendPhase.whoseTurn.game!!.players
            var inFrontOfWhom = sendPhase.inFrontOfWhom.location
            while (true) {
                inFrontOfWhom =
                    if (sendPhase.dir == direction.Left) (inFrontOfWhom + players.size - 1) % players.size
                    else (inFrontOfWhom + 1) % players.size
                if (players[inFrontOfWhom]!!.alive) {
                    logger.info("情报到达${players[inFrontOfWhom]}面前")
                    return ResolveResult(sendPhase.copy(inFrontOfWhom = players[inFrontOfWhom]!!), true)
                } else if (sendPhase.sender === players[inFrontOfWhom]) {
                    return nextTurn()
                }
            }
        }
    }

    private fun nextTurn(): ResolveResult {
        sendPhase.inFrontOfWhom.game!!.deck.discard(sendPhase.messageCard)
        if (!sendPhase.isMessageCardFaceUp) {
            val players = sendPhase.whoseTurn.game!!.players
            for (player in players) {
                if (player is HumanPlayer) {
                    val builder = notify_phase_toc.newBuilder()
                    builder.currentPlayerId = player.getAlternativeLocation(sendPhase.whoseTurn.location)
                    builder.currentPhase = Common.phase.Send_Phase
                    builder.messagePlayerId = player.getAlternativeLocation(sendPhase.inFrontOfWhom.location)
                    builder.messageCardDir = sendPhase.dir
                    builder.messageCard = sendPhase.messageCard.toPbCard()
                    builder.senderId = player.getAlternativeLocation(sendPhase.sender.location)
                    builder.waitingPlayerId = player.getAlternativeLocation(sendPhase.inFrontOfWhom.location)
                    player.send(builder.build())
                }
            }
        }
        return ResolveResult(NextTurn(sendPhase.whoseTurn), true)
    }
}