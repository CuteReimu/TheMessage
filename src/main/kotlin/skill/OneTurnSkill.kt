package com.fengsheng.skill

import com.fengsheng.Game

/**
 * 只有当前回合有效的技能
 */
interface OneTurnSkill : Skill {
    companion object {
        fun reset(game: Game) {
            for (p in game.players) {
                val skills = p!!.skills
                if (skills.any { it is OneTurnSkill })
                    p.skills = skills.filterNot { it is OneTurnSkill }.toTypedArray()
            }
        }
    }
}
