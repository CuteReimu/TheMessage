package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.card.Card
import org.apache.log4j.Logger

/**
 * 接收阶段（确定接收后，即将发动接收时的技能）
 *
 * @param whoseTurn     谁的回合
 * @param sender        情报传出者
 * @param messageCard   情报牌
 * @param inFrontOfWhom 情报在谁面前
 */
data class ReceivePhase(
    val whoseTurn: Player,
    val sender: Player,
    val messageCard: Card,
    val inFrontOfWhom: Player
) : Fsm {
    override fun resolve(): ResolveResult {
        val player = inFrontOfWhom
        if (player.alive) {
            player.messageCards.add(messageCard)
            log.info("${player}成功接收情报")
            for (p in player.game!!.players) p!!.notifyReceivePhase()
            val next = ReceivePhaseSkill(whoseTurn, sender, messageCard, inFrontOfWhom)
            next.receiveOrder.addPlayerIfHasThreeBlack(inFrontOfWhom)
            return ResolveResult(next, true)
        }
        player.game!!.deck.discard(messageCard)
        for (p in player.game!!.players) p!!.notifyReceivePhase()
        return ResolveResult(NextTurn(whoseTurn), true)
    }

    companion object {
        private val log = Logger.getLogger(ReceivePhase::class.java)
    }
}