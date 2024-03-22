package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.Player

/**
 * 影响游戏结果的技能
 */
interface ChangeGameResultSkill : Skill {
    /**
     * @param r 有技能的那个人
     * @param whoseTurn 谁的回合
     * @param declaredWinners 宣胜的玩家
     * @param winners 胜利的玩家
     */
    fun changeGameResult(r: Player, whoseTurn: Player, declaredWinners: MutableList<Player>, winners: MutableList<Player>)
}

fun Game.changeGameResult(whoseTurn: Player, declaredWinners: MutableList<Player>, winners: MutableList<Player>) {
    val beginLocation = whoseTurn.location
    var i = beginLocation
    do {
        val player = players[i]!!
        player.skills.forEach { skill ->
            if ((player.alive || !skill.isInitialSkill) && skill is ChangeGameResultSkill)
                skill.changeGameResult(player, whoseTurn, declaredWinners, winners)
        }
        i = (i + 1) % players.size
    } while (i != beginLocation)
}
