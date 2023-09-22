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
 */
data class OnGiveCard(
    val whoseTurn: Player,
    val fromPlayer: Player,
    val toPlayer: Player,
    val nextFsm: Fsm,
) : Fsm {
    override fun resolve(): ResolveResult {
        val result = whoseTurn.game!!.dealListeningSkill(fromPlayer.location)
        return result ?: ResolveResult(nextFsm, true)
    }

    override fun toString(): String {
        return "${fromPlayer}给${toPlayer}手牌后"
    }
}