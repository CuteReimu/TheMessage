package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult

/**
 * 当一名角色弃牌后
 *
 * @param whoseTurn 谁的回合
 * @param player 弃牌的角色
 * @param nextFsm 接下来是什么阶段
 * @param afterResolveFunc 结算后触发的一些效果，一般是用来清本回合使用次数
 */
data class OnDiscardCard(
    val whoseTurn: Player,
    val player: Player,
    val nextFsm: Fsm,
    val afterResolveFunc: () -> Unit = { },
) : Fsm {
    override fun resolve(): ResolveResult {
        val result = whoseTurn.game!!.dealListeningSkill(whoseTurn.location)
        if (result != null) return result
        afterResolveFunc()
        return ResolveResult(nextFsm, true)
    }

    override fun toString(): String {
        return "${player}弃牌后"
    }
}