package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.card.Card

/**
 * 接收情报时，传出者的技能
 */
class ReceivePhaseSenderSkill(
    /**
     * 谁的回合
     */
    val whoseTurn: Player,
    /**
     * 情报传出者
     */
    val sender: Player,
    /**
     * 情报牌
     */
    val messageCard: Card,
    /**
     * 情报在谁面前
     */
    val inFrontOfWhom: Player,
    /**
     * 接收第三张黑色情报牌的顺序（也就是后续结算死亡的顺序）
     */
    val receiveOrder: ReceiveOrder = ReceiveOrder()
) : Fsm {
    override fun resolve(): ResolveResult {
        val result = whoseTurn.game!!.dealListeningSkill()
        return result ?: ResolveResult(
            ReceivePhaseReceiverSkill(whoseTurn, messageCard, receiveOrder, inFrontOfWhom),
            true
        )
    }

    override fun toString(): String {
        return "${whoseTurn}的回合，${inFrontOfWhom}成功接收情报"
    }
}