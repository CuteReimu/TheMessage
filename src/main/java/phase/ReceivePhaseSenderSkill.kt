package com.fengsheng.phase

import com.fengsheng.Fsmimport

com.fengsheng.Playerimport com.fengsheng.ResolveResultimport com.fengsheng.card.Card
/**
 * 接收情报时，传出者的技能
 */
class ReceivePhaseSenderSkill(
    /**
     * 谁的回合
     */
    var whoseTurn: Player,
    /**
     * 情报牌
     */
    var messageCard: Card,
    /**
     * 情报在谁面前
     */
    var inFrontOfWhom: Player
) : Fsm {
    /**
     * 接收第三张黑色情报牌的顺序（也就是后续结算死亡的顺序）
     */
    val receiveOrder = ReceiveOrder()
    override fun resolve(): ResolveResult? {
        val result = whoseTurn.game.dealListeningSkill()
        return result
            ?: ResolveResult(
                ReceivePhaseReceiverSkill(whoseTurn, messageCard, receiveOrder, inFrontOfWhom),
                true
            )
    }

    override fun toString(): String {
        return whoseTurn.toString() + "的回合，" + inFrontOfWhom + "成功接收情报"
    }
}