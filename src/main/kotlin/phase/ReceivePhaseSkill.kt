package com.fengsheng.phase

import com.fengsheng.Player
import com.fengsheng.ProcessFsm
import com.fengsheng.ReceiveCardEvent
import com.fengsheng.ResolveResult
import com.fengsheng.card.Card

/**
 * 接收情报时的技能
 *
 * @param whoseTurn 谁的回合
 * @param messageCard 情报牌
 * @param receiveOrder 接收情报牌的顺序（也就是后续结算死亡的顺序）
 * @param inFrontOfWhom 情报在谁面前
 * @param receiveOrder 接收第三张黑色情报牌的顺序（也就是后续结算死亡的顺序）
 */
data class ReceivePhaseSkill(
    override val whoseTurn: Player,
    val sender: Player,
    val messageCard: Card,
    val inFrontOfWhom: Player,
) : ProcessFsm() {
    override fun onSwitch() {
        whoseTurn.game!!.addEvent(ReceiveCardEvent(whoseTurn, sender, messageCard, inFrontOfWhom))
    }

    override fun resolve0(): ResolveResult {
        return ResolveResult(NextTurn(whoseTurn), true)
    }

    override fun toString(): String {
        return "${whoseTurn}的回合，${inFrontOfWhom}成功接收情报"
    }
}