package com.fengsheng.phase

import com.fengsheng.Fsm
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.skill.JiangHuLing
import com.fengsheng.skill.JinBi
import com.fengsheng.skill.QiangLing

/**
 * 即将跳转到下一回合时
 *
 * @param player 当前回合的玩家（不是下回合的玩家）
 */
data class NextTurn(val player: Player) : Fsm {
    override fun resolve(): ResolveResult {
        val game = player.game!!
        if (game.checkOnlyOneAliveIdentityPlayers())
            return ResolveResult(null, false)
        var whoseTurn = player.location
        while (true) {
            whoseTurn = (whoseTurn + 1) % game.players.size
            val player = game.players[whoseTurn]!!
            if (player.alive) {
                game.players.forEach { it!!.resetSkillUseCount() }
                JinBi.resetJinBi(game)
                QiangLing.resetQiangLing(game)
                JiangHuLing.resetJiangHuLing(game)
                return ResolveResult(DrawPhase(player), true)
            }
        }
    }
}