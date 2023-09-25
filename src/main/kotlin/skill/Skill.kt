package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.Player

/**
 * 技能的基类
 */
interface Skill {
    /**
     * 获取技能ID
     */
    val skillId: SkillId
}

/**
 * 玩家初始拥有的技能。
 *
 * 只有[InitialSkill]会被无效。
 *
 * 技能产生出来的二段技能不会被无效，不要继承[InitialSkill]。
 */
interface InitialSkill : Skill

/**
 * 直到回合结束无效的技能
 */
class InvalidSkill private constructor(val originSkill: InitialSkill) : Skill {
    override val skillId = SkillId.INVALID

    companion object {
        fun deal(player: Player) {
            player.skills = player.skills.map { if (it is InitialSkill) InvalidSkill(it) else it }
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