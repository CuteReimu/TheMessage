package com.fengsheng.skill

abstract class AbstractSkill : Skill {
    override fun toString(): String {
        return skillId.toString()
    }
}