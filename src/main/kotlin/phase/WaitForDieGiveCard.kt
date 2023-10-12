package com.fengsheng.phase

import com.fengsheng.*

/**
 * 等待死亡角色给三张牌
 */
data class WaitForDieGiveCard(
    /**
     * 谁的回合
     */
    override val whoseTurn: Player,
    /**
     * 死亡的顺序
     */
    val diedQueue: List<Player>,
    /**
     * 死亡结算后的下一个动作
     */
    val afterDieResolve: Fsm
) : ProcessFsm() {
    override val needCheckWinAndDying = false
    override val needCheckDieSkill = true

    /**
     * 结算到dieQueue的第几个人的死亡给三张牌了
     */
    var diedIndex = 0

    override fun resolve0(): ResolveResult? {
        if (diedIndex >= diedQueue.size) return ResolveResult(afterDieResolve, true)
        val whoDie = diedQueue[diedIndex]
        if (whoDie.cards.isEmpty()) return ResolveResult(AfterDieGiveCard(this), true)
        for (p in whoDie.game!!.players) {
            p!!.waitForDieGiveCard(whoDie, Config.WaitSecond * 4 / 3)
        }
        return null
    }
}