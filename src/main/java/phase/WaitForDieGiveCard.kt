package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
/**
 * 等待死亡角色给三张牌
 */
class WaitForDieGiveCard(
    /**
     * 谁的回合
     */
    var whoseTurn: Player,
    /**
     * 死亡的顺序
     */
    var diedQueue: List<Player?>,
    /**
     * 在结算死亡技能时，又有新的人获得三张黑色情报的顺序
     */
    var receiveOrder: ReceiveOrder,
    /**
     * 死亡结算后的下一个动作
     */
    var afterDieResolve: Fsm?
) : Fsm {
    /**
     * 结算到dieQueue的第几个人的死亡给三张牌了
     */
    var diedIndex = 0
    override fun resolve(): ResolveResult? {
        if (diedIndex >= diedQueue.size) return ResolveResult(CheckWin(whoseTurn, receiveOrder, afterDieResolve), true)
        val whoDie = diedQueue[diedIndex]
        if (whoDie!!.cards.isEmpty()) return ResolveResult(AfterDieGiveCard(this), true)
        for (p in whoDie.game!!.players) {
            p!!.waitForDieGiveCard(whoDie, 20)
        }
        return null
    }
}