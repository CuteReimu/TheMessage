package com.fengsheng.phase

import com.fengsheng.ChooseReceiveCardEvent
import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.card.Card
import org.apache.logging.log4j.kotlin.logger

/**
 * 选择接收情报时
 *
 * @param whoseTurn           谁的回合（也就是情报传出者）
 * @param sender              情报传出者
 * @param messageCard         情报牌
 * @param inFrontOfWhom       情报在谁面前
 * @param isMessageCardFaceUp 情报是否面朝上
 */
data class OnChooseReceiveCard(
    override val whoseTurn: Player,
    val sender: Player,
    val messageCard: Card,
    val inFrontOfWhom: Player,
    val isMessageCardFaceUp: Boolean
) : Fsm {
    override fun resolve(): ResolveResult {
        logger.info("${inFrontOfWhom}选择接收情报")
        whoseTurn.game!!.addEvent(ChooseReceiveCardEvent(whoseTurn, inFrontOfWhom, messageCard, sender))
        for (p in whoseTurn.game!!.players) p!!.notifyChooseReceiveCard(inFrontOfWhom)
        return ResolveResult(
            FightPhaseIdle(whoseTurn, sender, messageCard, inFrontOfWhom, inFrontOfWhom, isMessageCardFaceUp),
            true
        )
    }
}
