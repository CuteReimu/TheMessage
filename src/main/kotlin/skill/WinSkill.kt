package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.Player

/**
 * 会导致直接宣胜的技能
 */
interface WinSkill : Skill {
    /**
     * @param r 有技能的那个人
     * @param whoseTurn 谁的回合
     * @param declaredWinner 宣胜的玩家
     * @param winner 胜利的玩家
     */
    fun checkWin(r: Player, whoseTurn: Player, declaredWinner: MutableMap<Int, Player>, winner: MutableMap<Int, Player>)
}

fun Game.checkWin(whoseTurn: Player, declaredWinner: MutableMap<Int, Player>, winner: MutableMap<Int, Player>) {
    val beginLocation = whoseTurn.location
    var i = beginLocation
    do {
        val player = players[i]!!
        player.skills.forEach { skill ->
            if ((player.alive || !skill.isInitialSkill) && skill is WinSkill)
                skill.checkWin(player, whoseTurn, declaredWinner, winner)
        }
        i = (i + 1) % players.size
    } while (i != beginLocation)
}
