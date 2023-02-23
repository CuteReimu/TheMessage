package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.card.Card

/**
 * 接收情报时，接收者的技能
 *
 * @param whoseTurn     谁的回合
 * @param messageCard   情报牌
 * @param receiveOrder  接收情报牌的顺序（也就是后续结算死亡的顺序）
 * @param inFrontOfWhom 情报在谁面前
 */
data class ReceivePhaseReceiverSkill(
    val whoseTurn: Player,
    val messageCard: Card,
    val receiveOrder: ReceiveOrder,
    val inFrontOfWhom: Player
) : Fsm {
    override fun resolve(): ResolveResult {
        val result = inFrontOfWhom.game!!.dealListeningSkill()
        return result ?: ResolveResult(CheckWin(whoseTurn, receiveOrder, NextTurn(whoseTurn)), true)
    }

    override fun toString(): String {
        return "${whoseTurn}的回合，${inFrontOfWhom}成功接收情报"
    }
}