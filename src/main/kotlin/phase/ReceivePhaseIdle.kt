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
 * @param sender 情报传出者
 * @param messageCard 情报牌
 * @param inFrontOfWhom 情报在谁面前
 */
class ReceivePhaseIdle(
    override val whoseTurn: Player,
    val sender: Player,
    var messageCard: Card,
    val inFrontOfWhom: Player,
) : ProcessFsm() {
    override fun onSwitch() {
        whoseTurn.game!!.addEvent(ReceiveCardEvent(this))
    }

    override fun resolve0(): ResolveResult {
        return ResolveResult(NextTurn(whoseTurn), true)
    }

    override fun toString(): String {
        return "${whoseTurn}的回合，${inFrontOfWhom}成功接收情报"
    }
}