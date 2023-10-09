package com.fengsheng.skill

import com.fengsheng.Config
import com.fengsheng.Player

/**
 * 影响摸牌阶段摸牌数量的技能
 */
interface ChangeDrawCardCountSkill : Skill {
    /**
     * @param oldCount 原数量
     * @return 新的数量
     */
    fun changeGameResult(player: Player, oldCount: Int): Int
}

/**
 * 获取玩家摸牌阶段摸牌的数量
 */
fun Player.getDrawCardCountEachTurn() = skills.fold(Config.HandCardCountEachTurn) { oldCount, skill ->
    if (skill is ChangeDrawCardCountSkill) skill.changeGameResult(this, oldCount) else oldCount
}
