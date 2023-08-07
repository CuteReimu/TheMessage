package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.Player

/**
 * 直到回合结束无效的技能
 */
class InvalidSkill private constructor(val originSkill: Skill) : AbstractSkill() {
    override val skillId = SkillId.INVALID

    companion object {
        fun deal(player: Player) {
            val skills = player.skills
            for ((i, skill) in skills.withIndex()) {
                if (skill !is InvalidSkill) skills[i] = InvalidSkill(skill)
            }
        }

        fun reset(game: Game) {
            for (player in game.players) {
                val skills = player!!.skills
                for ((i, skill) in skills.withIndex()) {
                    if (skill is InvalidSkill) skills[i] = skill.originSkill
                }
            }
        }
    }
}