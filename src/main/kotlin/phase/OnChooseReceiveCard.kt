package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.card.Card
import org.apache.log4j.Logger

/**
 * 选择接收情报时
 *
 * @param whoseTurn           谁的回合（也就是情报传出者）
 * @param messageCard         情报牌
 * @param inFrontOfWhom       情报在谁面前
 * @param isMessageCardFaceUp 情报是否面朝上
 */
data class OnChooseReceiveCard(
    val whoseTurn: Player,
    val messageCard: Card,
    val inFrontOfWhom: Player,
    val isMessageCardFaceUp: Boolean
) : Fsm {
    override fun resolve(): ResolveResult {
        val result = inFrontOfWhom.game!!.dealListeningSkill()
        if (result != null) return result
        log.info("${inFrontOfWhom}选择接收情报")
        for (p in whoseTurn.game!!.players) p!!.notifyChooseReceiveCard(inFrontOfWhom)
        return ResolveResult(
            FightPhaseIdle(whoseTurn, messageCard, inFrontOfWhom, inFrontOfWhom, isMessageCardFaceUp),
            true
        )
    }

    companion object {
        private val log = Logger.getLogger(OnChooseReceiveCard::class.java)
    }
}