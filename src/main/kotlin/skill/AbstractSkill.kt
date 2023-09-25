package com.fengsheng.skill

/**
 * 只有[AbstractSkill]会被无效。
 *
 * 技能产生出来的二段技能不会被无效，不要继承[AbstractSkill]。
 */
abstract class AbstractSkill : Skill {

    override fun toString(): String {
        return skillId.toString()
    }
}