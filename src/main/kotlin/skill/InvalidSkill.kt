package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.Player

/**
 * 直到回合结束无效的技能
 */
class InvalidSkill private constructor(val originSkill: AbstractSkill) : Skill {
    override val skillId = SkillId.INVALID

    companion object {
        fun deal(player: Player) {
            player.skills = player.skills.map { if (it is AbstractSkill) InvalidSkill(it) else it }
        }

        fun reset(game: Game) {
            for (player in game.players) {
                val skills = player!!.skills
                if (skills.any { it is InvalidSkill })
                    player.skills = skills.map { if (it is InvalidSkill) it.originSkill else it }
            }
        }
    }
}