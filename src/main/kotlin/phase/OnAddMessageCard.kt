package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult

/**
 * 当情报被置入情报区后
 * @param whoseTurn 谁的回合
 * @param afterResolve 结算完这个阶段后结算什么
 * @param bySkill 是否是因为角色技能置入情报区的
 * @param resolvingWhom 结算到谁的这个阶段了
 */
data class OnAddMessageCard(
    val whoseTurn: Player,
    val afterResolve: Fsm,
    val bySkill: Boolean = true,
    val resolvingWhom: Player = whoseTurn,
) : Fsm {
    override fun resolve(): ResolveResult {
        val game = whoseTurn.game!!
        val result = game.dealListeningSkill()
        if (result != null) return result
        var index = resolvingWhom.location
        while (true) {
            index = (index + 1) % game.players.size
            val player = game.players[index]!!
            if (player === whoseTurn)
                return ResolveResult(afterResolve, true)
            if (player.alive)
                return ResolveResult(copy(resolvingWhom = player), true)
        }
    }
}