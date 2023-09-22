package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult

/**
 * 当一名角色给另一名角色手牌后
 *
 * @param whoseTurn 谁的回合
 * @param fromPlayer 给牌的角色
 * @param toPlayer 被给牌的角色
 * @param nextFsm 接下来是什么阶段
 * @param afterResolveFunc 结算后触发的一些效果，一般是用来清本回合使用次数
 */
data class OnGiveCard(
    val whoseTurn: Player,
    val fromPlayer: Player,
    val toPlayer: Player,
    val nextFsm: Fsm,
    val afterResolveFunc: () -> Unit = { },
) : Fsm {
    override fun resolve(): ResolveResult {
        val result = whoseTurn.game!!.dealListeningSkill(fromPlayer.location)
        if (result != null) return result
        afterResolveFunc()
        return ResolveResult(nextFsm, true)
    }

    override fun toString(): String {
        return "${fromPlayer}给${toPlayer}手牌后"
    }
}