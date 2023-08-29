package com.fengsheng.phase

import com.fengsheng.Config
import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult

/**
 * 等待死亡角色给三张牌
 */
data class WaitForDieGiveCard(
    /**
     * 谁的回合
     */
    val whoseTurn: Player,
    /**
     * 死亡的顺序
     */
    val diedQueue: List<Player>,
    /**
     * 在结算死亡技能时，又有新的人获得三张黑色情报的顺序
     */
    override val receiveOrder: ReceiveOrder,
    /**
     * 死亡结算后的下一个动作
     */
    val afterDieResolve: Fsm
) : Fsm, HasReceiveOrder {
    /**
     * 结算到dieQueue的第几个人的死亡给三张牌了
     */
    var diedIndex = 0
    override fun resolve(): ResolveResult? {
        if (diedIndex >= diedQueue.size) return ResolveResult(CheckWin(whoseTurn, receiveOrder, afterDieResolve), true)
        val whoDie = diedQueue[diedIndex]
        if (whoDie.cards.isEmpty()) return ResolveResult(AfterDieGiveCard(this), true)
        for (p in whoDie.game!!.players) {
            p!!.waitForDieGiveCard(whoDie, Config.WaitSecond * 4 / 3)
        }
        return null
    }
}